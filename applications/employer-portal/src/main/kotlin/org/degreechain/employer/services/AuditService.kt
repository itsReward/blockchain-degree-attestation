package org.degreechain.employer.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class AuditEvent(
    val eventId: String,
    val eventType: String,
    val description: String,
    val organizationName: String,
    val userId: String?,
    val ipAddress: String?,
    val userAgent: String?,
    val timestamp: LocalDateTime,
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    val category: String, // AUTHENTICATION, VERIFICATION, PAYMENT, DATA_ACCESS
    val metadata: Map<String, Any> = emptyMap(),
    val resourceId: String? = null,
    val outcome: String? = null // SUCCESS, FAILURE, PENDING
)

@Service
class AuditService {

    // In-memory storage for demo purposes - in production this would be a database
    private val auditEvents = ConcurrentHashMap<String, AuditEvent>()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun logEvent(
        eventType: String,
        description: String,
        organizationName: String,
        userId: String? = null,
        ipAddress: String? = null,
        userAgent: String? = null,
        severity: String = "MEDIUM",
        category: String,
        metadata: Map<String, Any> = emptyMap(),
        resourceId: String? = null,
        outcome: String? = null
    ): String = withContext(Dispatchers.IO) {
        logger.debug { "Logging audit event: $eventType for organization: $organizationName" }

        val eventId = UUID.randomUUID().toString()
        val auditEvent = AuditEvent(
            eventId = eventId,
            eventType = eventType,
            description = description,
            organizationName = organizationName,
            userId = userId,
            ipAddress = ipAddress,
            userAgent = userAgent,
            timestamp = LocalDateTime.now(),
            severity = severity,
            category = category,
            metadata = metadata,
            resourceId = resourceId,
            outcome = outcome
        )

        auditEvents[eventId] = auditEvent

        // Log to application logs for immediate visibility
        when (severity) {
            "CRITICAL" -> logger.error { "AUDIT: $description" }
            "HIGH" -> logger.warn { "AUDIT: $description" }
            else -> logger.info { "AUDIT: $description" }
        }

        eventId
    }

    suspend fun getAuditTrail(
        organizationName: String,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?,
        category: String?,
        severity: String?,
        page: Int,
        size: Int
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving audit trail for organization: $organizationName" }

        val filteredEvents = auditEvents.values
            .filter { it.organizationName == organizationName }
            .filter { event ->
                startDate?.let { event.timestamp.isAfter(it) } ?: true
            }
            .filter { event ->
                endDate?.let { event.timestamp.isBefore(it) } ?: true
            }
            .filter { event ->
                category?.let { event.category == it } ?: true
            }
            .filter { event ->
                severity?.let { event.severity == it } ?: true
            }
            .sortedByDescending { it.timestamp }

        val startIndex = page * size
        val endIndex = minOf(startIndex + size, filteredEvents.size)
        val paginatedEvents = if (startIndex < filteredEvents.size) {
            filteredEvents.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        mapOf(
            "auditEvents" to paginatedEvents,
            "page" to page,
            "size" to size,
            "totalElements" to filteredEvents.size,
            "totalPages" to (filteredEvents.size + size - 1) / size,
            "hasNext" to (endIndex < filteredEvents.size),
            "hasPrevious" to (page > 0),
            "filters" to mapOf(
                "startDate" to startDate?.format(dateFormatter),
                "endDate" to endDate?.format(dateFormatter),
                "category" to category,
                "severity" to severity
            )
        )
    }

    suspend fun getAuditStatistics(
        organizationName: String,
        days: Int = 30
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Generating audit statistics for organization: $organizationName" }

        val cutoffDate = LocalDateTime.now().minusDays(days.toLong())
        val recentEvents = auditEvents.values
            .filter { it.organizationName == organizationName }
            .filter { it.timestamp.isAfter(cutoffDate) }

        val totalEvents = recentEvents.size
        val eventsByCategory = recentEvents.groupingBy { it.category }.eachCount()
        val eventsBySeverity = recentEvents.groupingBy { it.severity }.eachCount()
        val eventsByOutcome = recentEvents.groupingBy { it.outcome ?: "UNKNOWN" }.eachCount()
        val eventsByType = recentEvents.groupingBy { it.eventType }.eachCount()

        // Calculate daily event counts
        val dailyEvents = recentEvents
            .groupBy { it.timestamp.toLocalDate() }
            .mapValues { it.value.size }
            .toSortedMap()

        // Identify suspicious patterns
        val suspiciousPatterns = identifySuspiciousPatterns(recentEvents)

        // Calculate risk score
        val riskScore = calculateRiskScore(recentEvents)

        mapOf(
            "organizationName" to organizationName,
            "reportPeriodDays" to days,
            "totalEvents" to totalEvents,
            "eventsByCategory" to eventsByCategory,
            "eventsBySeverity" to eventsBySeverity,
            "eventsByOutcome" to eventsByOutcome,
            "eventsByType" to eventsByType,
            "dailyEventCounts" to dailyEvents,
            "suspiciousPatterns" to suspiciousPatterns,
            "riskScore" to riskScore,
            "averageEventsPerDay" to if (days > 0) totalEvents.toDouble() / days else 0.0,
            "lastAuditEvent" to recentEvents.maxByOrNull { it.timestamp }?.timestamp?.format(dateFormatter)
        )
    }

    suspend fun generateComplianceReport(
        organizationName: String,
        startDate: LocalDateTime,
        endDate: LocalDateTime
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Generating compliance report for organization: $organizationName" }

        val reportEvents = auditEvents.values
            .filter { it.organizationName == organizationName }
            .filter { it.timestamp.isAfter(startDate) && it.timestamp.isBefore(endDate) }

        val verificationEvents = reportEvents.filter { it.category == "VERIFICATION" }
        val authenticationEvents = reportEvents.filter { it.category == "AUTHENTICATION" }
        val paymentEvents = reportEvents.filter { it.category == "PAYMENT" }
        val dataAccessEvents = reportEvents.filter { it.category == "DATA_ACCESS" }

        val successfulVerifications = verificationEvents.count { it.outcome == "SUCCESS" }
        val failedVerifications = verificationEvents.count { it.outcome == "FAILURE" }
        val verificationSuccessRate = if (verificationEvents.isNotEmpty()) {
            successfulVerifications.toDouble() / verificationEvents.size
        } else {
            0.0
        }

        val failedAuthentications = authenticationEvents.count { it.outcome == "FAILURE" }
        val securityIncidents = reportEvents.count { it.severity in listOf("HIGH", "CRITICAL") }

        // Compliance metrics
        val complianceMetrics = mapOf(
            "dataRetentionCompliance" to checkDataRetentionCompliance(reportEvents),
            "accessControlCompliance" to checkAccessControlCompliance(authenticationEvents),
            "auditTrailCompleteness" to checkAuditTrailCompleteness(reportEvents),
            "securityStandards" to checkSecurityStandards(reportEvents)
        )

        mapOf(
            "organizationName" to organizationName,
            "reportPeriod" to mapOf(
                "startDate" to startDate.format(dateFormatter),
                "endDate" to endDate.format(dateFormatter)
            ),
            "eventSummary" to mapOf(
                "totalEvents" to reportEvents.size,
                "verificationEvents" to verificationEvents.size,
                "authenticationEvents" to authenticationEvents.size,
                "paymentEvents" to paymentEvents.size,
                "dataAccessEvents" to dataAccessEvents.size
            ),
            "verificationMetrics" to mapOf(
                "successfulVerifications" to successfulVerifications,
                "failedVerifications" to failedVerifications,
                "successRate" to verificationSuccessRate
            ),
            "securityMetrics" to mapOf(
                "failedAuthentications" to failedAuthentications,
                "securityIncidents" to securityIncidents,
                "suspiciousActivities" to identifySuspiciousPatterns(reportEvents).size
            ),
            "complianceMetrics" to complianceMetrics,
            "recommendations" to generateComplianceRecommendations(reportEvents, complianceMetrics),
            "reportGeneratedAt" to LocalDateTime.now().format(dateFormatter)
        )
    }

    suspend fun searchAuditEvents(
        organizationName: String,
        searchQuery: String,
        searchFields: List<String> = listOf("description", "eventType", "resourceId")
    ): List<AuditEvent> = withContext(Dispatchers.IO) {
        logger.debug { "Searching audit events for organization: $organizationName, query: $searchQuery" }

        val queryLower = searchQuery.lowercase()

        auditEvents.values
            .filter { it.organizationName == organizationName }
            .filter { event ->
                searchFields.any { field ->
                    when (field) {
                        "description" -> event.description.lowercase().contains(queryLower)
                        "eventType" -> event.eventType.lowercase().contains(queryLower)
                        "resourceId" -> event.resourceId?.lowercase()?.contains(queryLower) == true
                        "userId" -> event.userId?.lowercase()?.contains(queryLower) == true
                        else -> false
                    }
                }
            }
            .sortedByDescending { it.timestamp }
    }

    private fun identifySuspiciousPatterns(events: List<AuditEvent>): List<Map<String, Any>> {
        val patterns = mutableListOf<Map<String, Any>>()

        // Pattern 1: Multiple failed authentications from same IP
        val failedAuthsByIP = events
            .filter { it.category == "AUTHENTICATION" && it.outcome == "FAILURE" }
            .groupBy { it.ipAddress }
            .filter { it.value.size >= 5 }

        failedAuthsByIP.forEach { (ip, failures) ->
            patterns.add(mapOf(
                "pattern" to "MULTIPLE_FAILED_AUTHENTICATIONS",
                "description" to "Multiple failed authentication attempts from IP: $ip",
                "severity" to "HIGH",
                "count" to failures.size,
                "ipAddress" to ip
            ))
        }

        // Pattern 2: Unusual verification volume
        val hourlyVerifications = events
            .filter { it.category == "VERIFICATION" }
            .groupBy { it.timestamp.hour }
            .mapValues { it.value.size }

        val avgVerificationsPerHour = hourlyVerifications.values.average()
        hourlyVerifications.forEach { (hour, count) ->
            if (count > avgVerificationsPerHour * 3) {
                patterns.add(mapOf(
                    "pattern" to "UNUSUAL_VERIFICATION_VOLUME",
                    "description" to "Unusually high verification volume at hour $hour",
                    "severity" to "MEDIUM",
                    "count" to count,
                    "hour" to hour
                ))
            }
        }

        return patterns
    }

    private fun calculateRiskScore(events: List<AuditEvent>): Double {
        var score = 0.0

        // Base score calculation
        val criticalEvents = events.count { it.severity == "CRITICAL" }
        val highEvents = events.count { it.severity == "HIGH" }
        val failedEvents = events.count { it.outcome == "FAILURE" }

        score += criticalEvents * 10.0
        score += highEvents * 5.0
        score += failedEvents * 2.0

        // Normalize to 0-100 scale
        val maxPossibleScore = events.size * 10.0
        return if (maxPossibleScore > 0) (score / maxPossibleScore) * 100 else 0.0
    }

    private fun checkDataRetentionCompliance(events: List<AuditEvent>): Boolean {
        // Check if audit events are being properly retained
        val oldestEvent = events.minByOrNull { it.timestamp }
        val retentionPeriod = 365 // days

        return oldestEvent?.let {
            it.timestamp.isAfter(LocalDateTime.now().minusDays(retentionPeriod.toLong()))
        } ?: true
    }

    private fun checkAccessControlCompliance(authEvents: List<AuditEvent>): Boolean {
        // Check if access control events are properly logged
        val totalAuthAttempts = authEvents.size
        val successfulAuth = authEvents.count { it.outcome == "SUCCESS" }

        return totalAuthAttempts > 0 && (successfulAuth.toDouble() / totalAuthAttempts) > 0.8
    }

    private fun checkAuditTrailCompleteness(events: List<AuditEvent>): Boolean {
        // Check if all required event categories are present
        val requiredCategories = setOf("AUTHENTICATION", "VERIFICATION", "PAYMENT", "DATA_ACCESS")
        val presentCategories = events.map { it.category }.toSet()

        return requiredCategories.all { it in presentCategories }
    }

    private fun checkSecurityStandards(events: List<AuditEvent>): Boolean {
        // Check if security incidents are within acceptable limits
        val securityIncidents = events.count { it.severity in listOf("HIGH", "CRITICAL") }
        val totalEvents = events.size

        return if (totalEvents > 0) {
            (securityIncidents.toDouble() / totalEvents) < 0.05 // Less than 5% incidents
        } else {
            true
        }
    }

    private fun generateComplianceRecommendations(
        events: List<AuditEvent>,
        complianceMetrics: Map<String, Boolean>
    ): List<String> {
        val recommendations = mutableListOf<String>()

        if (complianceMetrics["accessControlCompliance"] == false) {
            recommendations.add("Implement stronger access control measures and monitor authentication failures")
        }

        if (complianceMetrics["securityStandards"] == false) {
            recommendations.add("Review and enhance security protocols to reduce incident rate")
        }

        if (complianceMetrics["auditTrailCompleteness"] == false) {
            recommendations.add("Ensure all system activities are properly logged across all categories")
        }

        val highSeverityEvents = events.count { it.severity in listOf("HIGH", "CRITICAL") }
        if (highSeverityEvents > 10) {
            recommendations.add("Investigate and address the high number of critical security events")
        }

        if (recommendations.isEmpty()) {
            recommendations.add("Compliance status is satisfactory. Continue monitoring.")
        }

        return recommendations
    }
}