package org.degreechain.gateway.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class RateLimitRule(
    val requests: Int,
    val windowMinutes: Int,
    val description: String
)

data class ClientRequestInfo(
    val requestCount: Int,
    val windowStart: LocalDateTime,
    val lastRequestTime: LocalDateTime
)

@Service
class RateLimitingService {

    // Configuration
    @Value("\${rate-limiting.default.requests-per-minute:60}")
    private var defaultRequestsPerMinute: Int = 60

    @Value("\${rate-limiting.burst.requests-per-minute:120}")
    private var burstRequestsPerMinute: Int = 120

    @Value("\${rate-limiting.enabled:true}")
    private var rateLimitingEnabled: Boolean = true

    // In-memory storage for request tracking
    private val clientRequests = ConcurrentHashMap<String, ClientRequestInfo>()

    // Rate limit rules by endpoint pattern
    private val endpointRules = mapOf(
        "/api/v1/verification" to RateLimitRule(30, 1, "Verification requests limited to prevent abuse"),
        "/api/v1/degrees/submit" to RateLimitRule(10, 1, "Degree submission rate limited"),
        "/api/v1/payments" to RateLimitRule(20, 1, "Payment requests limited for security"),
        "/api/v1/auth" to RateLimitRule(5, 1, "Authentication attempts limited"),
        "default" to RateLimitRule(defaultRequestsPerMinute, 1, "Default rate limit")
    )

    suspend fun checkRateLimit(
        clientId: String,
        endpoint: String,
        userRole: String? = null
    ): RateLimitResult = withContext(Dispatchers.IO) {

        if (!rateLimitingEnabled) {
            return@withContext RateLimitResult(
                allowed = true,
                remainingRequests = Int.MAX_VALUE,
                resetTime = null,
                rule = "Rate limiting disabled"
            )
        }

        logger.debug { "Checking rate limit for client: $clientId, endpoint: $endpoint" }

        val rule = determineRateLimit(endpoint, userRole)
        val now = LocalDateTime.now()

        // Clean up old entries
        cleanupExpiredEntries(now, rule.windowMinutes)

        val clientInfo = clientRequests[clientId]
        val windowStart = now.minus(rule.windowMinutes.toLong(), ChronoUnit.MINUTES)

        val currentCount = if (clientInfo == null || clientInfo.windowStart.isBefore(windowStart)) {
            // Start new window
            clientRequests[clientId] = ClientRequestInfo(1, now, now)
            1
        } else {
            // Update existing window
            val newCount = clientInfo.requestCount + 1
            clientRequests[clientId] = clientInfo.copy(
                requestCount = newCount,
                lastRequestTime = now
            )
            newCount
        }

        val allowed = currentCount <= rule.requests
        val remainingRequests = maxOf(0, rule.requests - currentCount)
        val resetTime = clientInfo?.windowStart?.plus(rule.windowMinutes.toLong(), ChronoUnit.MINUTES)

        if (!allowed) {
            logger.warn { "Rate limit exceeded for client: $clientId, endpoint: $endpoint, count: $currentCount" }
        }

        RateLimitResult(
            allowed = allowed,
            remainingRequests = remainingRequests,
            resetTime = resetTime,
            rule = rule.description,
            currentRequests = currentCount,
            windowStart = clientInfo?.windowStart
        )
    }

    suspend fun getClientStatistics(clientId: String): Map<String, Any> = withContext(Dispatchers.IO) {
        val clientInfo = clientRequests[clientId]

        if (clientInfo == null) {
            return@withContext mapOf(
                "clientId" to clientId,
                "status" to "NO_ACTIVITY",
                "message" to "No recent activity recorded"
            )
        }

        val now = LocalDateTime.now()
        val minutesSinceWindowStart = ChronoUnit.MINUTES.between(clientInfo.windowStart, now)
        val minutesSinceLastRequest = ChronoUnit.MINUTES.between(clientInfo.lastRequestTime, now)

        mapOf(
            "clientId" to clientId,
            "currentRequests" to clientInfo.requestCount,
            "windowStart" to clientInfo.windowStart.toString(),
            "lastRequestTime" to clientInfo.lastRequestTime.toString(),
            "minutesSinceWindowStart" to minutesSinceWindowStart,
            "minutesSinceLastRequest" to minutesSinceLastRequest,
            "status" to if (minutesSinceLastRequest > 5) "INACTIVE" else "ACTIVE"
        )
    }

    suspend fun getAllClientStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val activeClients = clientRequests.entries.filter { (_, info) ->
            ChronoUnit.MINUTES.between(info.lastRequestTime, now) <= 10
        }

        val totalRequests = clientRequests.values.sumOf { it.requestCount }
        val averageRequestsPerClient = if (activeClients.isNotEmpty()) {
            totalRequests.toDouble() / activeClients.size
        } else {
            0.0
        }

        mapOf(
            "totalClients" to clientRequests.size,
            "activeClients" to activeClients.size,
            "totalRequests" to totalRequests,
            "averageRequestsPerClient" to averageRequestsPerClient,
            "topClients" to getTopClients(),
            "rateLimitingEnabled" to rateLimitingEnabled,
            "timestamp" to now.toString()
        )
    }

    suspend fun resetClientLimit(clientId: String): Boolean = withContext(Dispatchers.IO) {
        logger.info { "Resetting rate limit for client: $clientId" }

        val removed = clientRequests.remove(clientId)
        if (removed != null) {
            logger.info { "Successfully reset rate limit for client: $clientId" }
            true
        } else {
            logger.warn { "No rate limit data found for client: $clientId" }
            false
        }
    }

    suspend fun updateRateLimitConfig(
        requestsPerMinute: Int? = null,
        burstRequests: Int? = null,
        enabled: Boolean? = null
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Updating rate limit configuration" }

        requestsPerMinute?.let {
            defaultRequestsPerMinute = it
            logger.info { "Updated default requests per minute to: $it" }
        }

        burstRequests?.let {
            burstRequestsPerMinute = it
            logger.info { "Updated burst requests per minute to: $it" }
        }

        enabled?.let {
            rateLimitingEnabled = it
            logger.info { "Updated rate limiting enabled to: $it" }
        }

        mapOf(
            "defaultRequestsPerMinute" to defaultRequestsPerMinute,
            "burstRequestsPerMinute" to burstRequestsPerMinute,
            "rateLimitingEnabled" to rateLimitingEnabled,
            "message" to "Configuration updated successfully"
        )
    }

    suspend fun getViolationReport(hours: Int = 24): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Generating violation report for last $hours hours" }

        val now = LocalDateTime.now()
        val cutoffTime = now.minus(hours.toLong(), ChronoUnit.HOURS)

        // In a real implementation, this would query violation logs
        // For now, return mock data based on current client data
        val violations = clientRequests.entries.filter { (_, info) ->
            info.lastRequestTime.isAfter(cutoffTime) && info.requestCount > defaultRequestsPerMinute
        }.map { (clientId, info) ->
            mapOf(
                "clientId" to clientId,
                "requestCount" to info.requestCount,
                "excessRequests" to (info.requestCount - defaultRequestsPerMinute),
                "lastViolationTime" to info.lastRequestTime.toString(),
                "severity" to when {
                    info.requestCount > defaultRequestsPerMinute * 3 -> "HIGH"
                    info.requestCount > defaultRequestsPerMinute * 2 -> "MEDIUM"
                    else -> "LOW"
                }
            )
        }

        mapOf(
            "reportPeriodHours" to hours,
            "totalViolations" to violations.size,
            "violations" to violations,
            "mostViolatingClient" to violations.maxByOrNull {
                it["excessRequests"] as Int
            }?.get("clientId"),
            "averageExcessRequests" to if (violations.isNotEmpty()) {
                violations.map { it["excessRequests"] as Int }.average()
            } else {
                0.0
            },
            "timestamp" to now.toString()
        )
    }

    private fun determineRateLimit(endpoint: String, userRole: String?): RateLimitRule {
        // Check for specific endpoint rules
        val endpointRule = endpointRules.entries.find { (pattern, _) ->
            endpoint.contains(pattern, ignoreCase = true)
        }?.value

        if (endpointRule != null) {
            return endpointRule
        }

        // Apply role-based rules
        return when (userRole?.uppercase()) {
            "ADMIN" -> RateLimitRule(defaultRequestsPerMinute * 2, 1, "Admin user - increased limit")
            "PREMIUM" -> RateLimitRule((defaultRequestsPerMinute * 1.5).toInt(), 1, "Premium user - increased limit")
            "UNIVERSITY" -> RateLimitRule(defaultRequestsPerMinute, 1, "University user - standard limit")
            "EMPLOYER" -> RateLimitRule(defaultRequestsPerMinute, 1, "Employer user - standard limit")
            else -> endpointRules["default"]!!
        }
    }

    private fun cleanupExpiredEntries(now: LocalDateTime, windowMinutes: Int) {
        val cutoffTime = now.minus(windowMinutes.toLong() * 2, ChronoUnit.MINUTES)

        val expiredKeys = clientRequests.entries.filter { (_, info) ->
            info.lastRequestTime.isBefore(cutoffTime)
        }.map { it.key }

        expiredKeys.forEach { key ->
            clientRequests.remove(key)
        }

        if (expiredKeys.isNotEmpty()) {
            logger.debug { "Cleaned up ${expiredKeys.size} expired rate limit entries" }
        }
    }

    private fun getTopClients(): List<Map<String, Any>> {
        return clientRequests.entries
            .sortedByDescending { it.value.requestCount }
            .take(10)
            .map { (clientId, info) ->
                mapOf(
                    "clientId" to clientId,
                    "requestCount" to info.requestCount,
                    "lastRequestTime" to info.lastRequestTime.toString()
                )
            }
    }

    suspend fun isClientBlocked(clientId: String): Boolean = withContext(Dispatchers.IO) {
        val clientInfo = clientRequests[clientId] ?: return@withContext false

        // Block if client has made excessive requests in a short time
        val now = LocalDateTime.now()
        val recentMinutes = ChronoUnit.MINUTES.between(clientInfo.windowStart, now)

        return@withContext if (recentMinutes <= 1) {
            clientInfo.requestCount > burstRequestsPerMinute
        } else {
            false
        }
    }

    suspend fun blockClient(clientId: String, durationMinutes: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.warn { "Blocking client: $clientId for $durationMinutes minutes" }

        val now = LocalDateTime.now()
        val blockUntil = now.plus(durationMinutes.toLong(), ChronoUnit.MINUTES)

        // Set a very high request count to effectively block the client
        clientRequests[clientId] = ClientRequestInfo(
            requestCount = Int.MAX_VALUE,
            windowStart = now,
            lastRequestTime = now
        )

        mapOf(
            "clientId" to clientId,
            "blocked" to true,
            "blockDurationMinutes" to durationMinutes,
            "blockUntil" to blockUntil.toString(),
            "reason" to "Manual block",
            "timestamp" to now.toString()
        )
    }

    suspend fun unblockClient(clientId: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Unblocking client: $clientId" }

        val wasBlocked = clientRequests.remove(clientId) != null

        mapOf(
            "clientId" to clientId,
            "unblocked" to wasBlocked,
            "message" to if (wasBlocked) "Client unblocked successfully" else "Client was not blocked",
            "timestamp" to LocalDateTime.now().toString()
        )
    }

    suspend fun getRateLimitConfiguration(): Map<String, Any> = withContext(Dispatchers.IO) {
        mapOf(
            "enabled" to rateLimitingEnabled,
            "defaultRequestsPerMinute" to defaultRequestsPerMinute,
            "burstRequestsPerMinute" to burstRequestsPerMinute,
            "endpointRules" to endpointRules.mapValues { (_, rule) ->
                mapOf(
                    "requests" to rule.requests,
                    "windowMinutes" to rule.windowMinutes,
                    "description" to rule.description
                )
            },
            "activeClients" to clientRequests.size,
            "memoryUsage" to getMemoryUsage()
        )
    }

    private fun getMemoryUsage(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val totalMemory = runtime.totalMemory()
        val freeMemory = runtime.freeMemory()
        val usedMemory = totalMemory - freeMemory
        val maxMemory = runtime.maxMemory()

        return mapOf(
            "usedMemoryMB" to usedMemory / (1024 * 1024),
            "totalMemoryMB" to totalMemory / (1024 * 1024),
            "maxMemoryMB" to maxMemory / (1024 * 1024),
            "freeMemoryMB" to freeMemory / (1024 * 1024),
            "memoryUsagePercent" to ((usedMemory.toDouble() / maxMemory) * 100).toInt()
        )
    }
}

data class RateLimitResult(
    val allowed: Boolean,
    val remainingRequests: Int,
    val resetTime: LocalDateTime?,
    val rule: String,
    val currentRequests: Int? = null,
    val windowStart: LocalDateTime? = null
)