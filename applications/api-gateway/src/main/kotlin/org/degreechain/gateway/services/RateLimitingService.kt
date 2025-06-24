package org.degreechain.gateway.services

import mu.KotlinLogging
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class RateLimitingService(
    private val redisTemplate: RedisTemplate<String, String>
)
{

    companion object {
        private const val RATE_LIMIT_PREFIX = "rate_limit:"
        private const val DEFAULT_REQUESTS_PER_MINUTE = 60
        private const val BURST_REQUESTS_PER_MINUTE = 120
    }

    fun checkRateLimit(clientId: String, endpoint: String = "default"): RateLimitResult {
        val key = "$RATE_LIMIT_PREFIX$clientId:$endpoint"
        val window = getTimeWindow()
        val windowKey = "$key:$window"

        return try {
            val operations = redisTemplate.opsForValue()
            val currentCount = operations.get(windowKey)?.toIntOrNull() ?: 0
            val limit = getLimit(endpoint)

            if (currentCount >= limit) {
                logger.debug { "Rate limit exceeded for client: $clientId, endpoint: $endpoint" }
                RateLimitResult(
                    allowed = false,
                    currentCount = currentCount,
                    limit = limit,
                    resetTime = getResetTime(),
                    retryAfter = getRetryAfter()
                )
            } else {
                // Increment counter
                operations.increment(windowKey)
                redisTemplate.expire(windowKey, Duration.ofMinutes(1))

                RateLimitResult(
                    allowed = true,
                    currentCount = currentCount + 1,
                    limit = limit,
                    resetTime = getResetTime(),
                    retryAfter = null
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Error checking rate limit for client: $clientId" }
            // Allow request if Redis is unavailable
            RateLimitResult(
                allowed = true,
                currentCount = 0,
                limit = getLimit(endpoint),
                resetTime = getResetTime(),
                retryAfter = null
            )
        }
    }

    fun getAllClientStatistics(){

    }


    fun getRateLimitInfo(clientId: String, endpoint: String = "default"): Map<String, Any> {
        val key = "$RATE_LIMIT_PREFIX$clientId:$endpoint"
        val window = getTimeWindow()
        val windowKey = "$key:$window"

        return try {
            val currentCount = redisTemplate.opsForValue().get(windowKey)?.toIntOrNull() ?: 0
            val limit = getLimit(endpoint)

            mapOf(
                "currentCount" to currentCount,
                "limit" to limit,
                "resetTime" to getResetTime(),
                "remaining" to maxOf(0, limit - currentCount)
            )
        } catch (e: Exception) {
            logger.error(e) { "Error getting rate limit info for client: $clientId" }
            mapOf(
                "currentCount" to 0,
                "limit" to getLimit(endpoint),
                "resetTime" to getResetTime(),
                "remaining" to getLimit(endpoint)
            )
        }
    }

    private fun getTimeWindow(): String {
        val now = LocalDateTime.now()
        return "${now.year}-${now.monthValue.toString().padStart(2, '0')}-${now.dayOfMonth.toString().padStart(2, '0')}-${now.hour}-${now.minute}"
    }

    private fun getLimit(endpoint: String): Int {
        return when {
            endpoint.contains("auth") -> BURST_REQUESTS_PER_MINUTE
            endpoint.contains("health") -> BURST_REQUESTS_PER_MINUTE * 2
            else -> DEFAULT_REQUESTS_PER_MINUTE
        }
    }

    private fun getResetTime(): String {
        val now = LocalDateTime.now()
        val resetTime = now.plusMinutes(1).withSecond(0).withNano(0)
        return resetTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    private fun getRetryAfter(): Long {
        val now = LocalDateTime.now()
        val nextMinute = now.plusMinutes(1).withSecond(0).withNano(0)
        return Duration.between(now, nextMinute).seconds
    }


}


