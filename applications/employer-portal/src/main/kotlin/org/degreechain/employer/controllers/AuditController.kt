package org.degreechain.employer.controllers

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.employer.services.AuditService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/audit")
@CrossOrigin(origins = ["http://localhost:3000"])
class AuditController(
    private val auditService: AuditService
) {

    @PostMapping("/events")
    fun logAuditEvent(
        @RequestBody request: LogAuditEventRequest
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Logging audit event: ${request.eventType} for organization: ${request.organizationName}" }

        try {
            val eventId = auditService.logEvent(
                eventType = request.eventType,
                description = request.description,
                organizationName = request.organizationName,
                userId = request.userId,
                ipAddress = request.ipAddress,
                userAgent = request.userAgent,
                severity = request.severity,
                category = request.category,
                metadata = request.metadata,
                resourceId = request.resourceId,
                outcome = request.outcome
            )

            ResponseEntity.ok(
                mapOf(
                    "success" to true,
                    "eventId" to eventId,
                    "message" to "Audit event logged successfully"
                )
            )
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to log audit event" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorCode" to e.errorCode.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error logging audit event" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to "Internal server error occurred"
                )
            )
        }
    }

    @GetMapping("/events")
    fun getAuditEvents(
        @RequestParam organizationName: String,
        @RequestParam(defaultValue = "0") @Min(0) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) size: Int,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Retrieving audit events for organization: $organizationName" }

        try {
            val result = auditService.getAuditEvents(
                organizationName = organizationName,
                page = page,
                size = size,
                category = category,
                severity = severity,
                startDate = startDate,
                endDate = endDate
            )

            ResponseEntity.ok(result)
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to retrieve audit events" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorCode" to e.errorCode.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error retrieving audit events" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to "Internal server error occurred"
                )
            )
        }
    }

    @GetMapping("/reports/compliance")
    fun generateComplianceReport(
        @RequestParam organizationName: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Generating compliance report for organization: $organizationName" }

        try {
            val report = auditService.generateComplianceReport(
                organizationName = organizationName,
                startDate = startDate ?: LocalDateTime.now().minusDays(30),
                endDate = endDate ?: LocalDateTime.now()
            )

            ResponseEntity.ok(report)
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to generate compliance report" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorCode" to e.errorCode.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error generating compliance report" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to "Internal server error occurred"
                )
            )
        }
    }

    @GetMapping("/reports/security")
    fun generateSecurityReport(
        @RequestParam organizationName: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Generating security report for organization: $organizationName" }

        try {
            val report = auditService.generateSecurityReport(
                organizationName = organizationName,
                startDate = startDate ?: LocalDateTime.now().minusDays(30),
                endDate = endDate ?: LocalDateTime.now()
            )

            ResponseEntity.ok(report)
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to generate security report" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorCode" to e.errorCode.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error generating security report" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to "Internal server error occurred"
                )
            )
        }
    }

    @GetMapping("/reports/audit-trail")
    fun downloadAuditTrail(
        @RequestParam organizationName: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?,
        @RequestParam(defaultValue = "csv") format: String
    ): ResponseEntity<ByteArray> = runBlocking {
        logger.info { "Downloading audit trail for organization: $organizationName in format: $format" }

        try {
            val auditTrail = auditService.downloadAuditTrail(
                organizationName = organizationName,
                startDate = startDate ?: LocalDateTime.now().minusDays(30),
                endDate = endDate ?: LocalDateTime.now(),
                format = format
            )

            val filename = "audit-trail-$organizationName-${System.currentTimeMillis()}.$format"
            val contentType = when (format.lowercase()) {
                "csv" -> MediaType.TEXT_PLAIN
                "json" -> MediaType.APPLICATION_JSON
                else -> MediaType.APPLICATION_OCTET_STREAM
            }

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .contentType(contentType)
                .body(auditTrail)
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to download audit trail" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Error: ${e.message}".toByteArray())
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error downloading audit trail" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal server error occurred".toByteArray())
        }
    }

    @GetMapping("/statistics")
    fun getAuditStatistics(
        @RequestParam organizationName: String,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) startDate: LocalDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) endDate: LocalDateTime?
    ): ResponseEntity<Map<String, Any>> = runBlocking {
        logger.info { "Retrieving audit statistics for organization: $organizationName" }

        try {
            val statistics = auditService.getAuditStatistics(
                organizationName = organizationName,
                startDate = startDate ?: LocalDateTime.now().minusDays(30),
                endDate = endDate ?: LocalDateTime.now()
            )

            ResponseEntity.ok(statistics)
        } catch (e: BusinessException) {
            logger.error(e) { "Failed to retrieve audit statistics" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                mapOf(
                    "success" to false,
                    "error" to e.message,
                    "errorCode" to e.errorCode.name
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error retrieving audit statistics" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                mapOf(
                    "success" to false,
                    "error" to "Internal server error occurred"
                )
            )
        }
    }
}

data class LogAuditEventRequest(
    val eventType: String,
    val description: String,
    val organizationName: String,
    val userId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val severity: String = "MEDIUM",
    val category: String,
    val metadata: Map<String, Any> = emptyMap(),
    val resourceId: String? = null,
    val outcome: String? = null
)