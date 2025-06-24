package org.degreechain.gateway.controllers

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.gateway.services.IntegrationService
import org.degreechain.gateway.services.AuthenticationService
import org.degreechain.gateway.services.UserInfo
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.validation.annotation.Validated
import jakarta.servlet.http.HttpServletRequest
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.LocalDateTime
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Controller for handling external integrations, webhooks, and third-party API interactions
 * Supports university system integrations, government API connections, and payment gateway webhooks
 */
@RestController
@RequestMapping("/api/v1/integrations")
@Validated
class IntegrationController(
    private val integrationService: IntegrationService,
    private val authenticationService: AuthenticationService
) {

    // ===== UNIVERSITY SYSTEM INTEGRATIONS =====

    /**
     * Sync degrees from university's existing student information system
     */
    @PostMapping("/university/{universityCode}/sync")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    suspend fun syncUniversityData(
        @PathVariable @Pattern(regexp = "^[A-Z0-9]{3,10}$") universityCode: String,
        @RequestParam(defaultValue = "false") fullSync: Boolean,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val user = validateAndGetUser(authorization)

            logger.info { "Starting university data sync for: $universityCode by user: ${user.username}" }

            val syncResult = integrationService.syncUniversityData(
                universityCode = universityCode,
                fullSync = fullSync,
                startDate = startDate,
                endDate = endDate,
                initiatedBy = user.userId
            )

            ResponseEntity.ok(ApiResponse.success(syncResult, "University data sync initiated"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to sync university data for: $universityCode" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Sync failed: ${e.message}"))
        }
    }

    /**
     * Upload bulk degree data via CSV/Excel file
     */
    @PostMapping("/university/{universityCode}/bulk-upload")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    suspend fun bulkUploadDegrees(
        @PathVariable @Pattern(regexp = "^[A-Z0-9]{3,10}$") universityCode: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam(defaultValue = "false") validateOnly: Boolean,
        @RequestParam(defaultValue = "true") skipDuplicates: Boolean,
        @RequestHeader("Authorization") authorization: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val user = validateAndGetUser(authorization)

            // Validate file
            if (file.isEmpty) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("File is empty"))
            }

            val allowedTypes = setOf("text/csv", "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            if (file.contentType !in allowedTypes) {
                return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Invalid file type. Only CSV and Excel files are allowed."))
            }

            logger.info { "Processing bulk upload for university: $universityCode, file: ${file.originalFilename}" }

            val uploadResult = integrationService.processBulkDegreeUpload(
                universityCode = universityCode,
                file = file,
                validateOnly = validateOnly,
                skipDuplicates = skipDuplicates,
                uploadedBy = user.userId
            )

            ResponseEntity.ok(ApiResponse.success(uploadResult, "Bulk upload processed"))
        } catch (e: Exception) {
            logger.error(e) { "Bulk upload failed for university: $universityCode" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Upload failed: ${e.message}"))
        }
    }

    /**
     * Get integration status for a university
     */
    @GetMapping("/university/{universityCode}/status")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    suspend fun getUniversityIntegrationStatus(
        @PathVariable @Pattern(regexp = "^[A-Z0-9]{3,10}$") universityCode: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val status = integrationService.getUniversityIntegrationStatus(universityCode)
            ResponseEntity.ok(ApiResponse.success(status, "Integration status retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get integration status for: $universityCode" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Failed to retrieve status: ${e.message}"))
        }
    }

    // ===== GOVERNMENT API INTEGRATIONS =====

    /**
     * Verify university accreditation status with government education ministry
     */
    @PostMapping("/government/verify-accreditation")
    @PreAuthorize("hasRole('ATTESTATION_AUTHORITY') or hasRole('ADMIN')")
    suspend fun verifyUniversityAccreditation(
        @RequestBody @Valid request: AccreditationVerificationRequest
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Verifying accreditation for university: ${request.universityCode}" }

            val verificationResult = integrationService.verifyUniversityAccreditation(
                universityCode = request.universityCode,
                country = request.country,
                registrationNumber = request.registrationNumber
            )

            ResponseEntity.ok(ApiResponse.success(verificationResult, "Accreditation verification completed"))
        } catch (e: Exception) {
            logger.error(e) { "Accreditation verification failed for: ${request.universityCode}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Verification failed: ${e.message}"))
        }
    }

    /**
     * Submit degree attestation to government authorities
     */
    @PostMapping("/government/submit-attestation")
    @PreAuthorize("hasRole('ATTESTATION_AUTHORITY')")
    suspend fun submitGovernmentAttestation(
        @RequestBody @Valid request: GovernmentAttestationRequest
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Submitting government attestation for degree: ${request.certificateNumber}" }

            val attestationResult = integrationService.submitGovernmentAttestation(
                certificateNumber = request.certificateNumber,
                governmentAgency = request.governmentAgency,
                country = request.country,
                additionalDocuments = request.additionalDocuments
            )

            ResponseEntity.ok(ApiResponse.success(attestationResult, "Government attestation submitted"))
        } catch (e: Exception) {
            logger.error(e) { "Government attestation failed for: ${request.certificateNumber}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Attestation failed: ${e.message}"))
        }
    }

    // ===== PAYMENT GATEWAY WEBHOOKS =====

    /**
     * Handle payment gateway webhooks
     */
    @PostMapping("/webhooks/payment/{provider}")
    suspend fun handlePaymentWebhook(
        @PathVariable provider: String,
        @RequestBody payload: Map<String, Any>,
        @RequestHeader("X-Signature", required = false) signature: String?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            logger.info { "Received payment webhook from provider: $provider" }

            // Verify webhook signature
            val isValidSignature = integrationService.verifyWebhookSignature(
                provider = provider,
                payload = payload,
                signature = signature,
                rawBody = request.inputStream.readBytes()
            )

            if (!isValidSignature) {
                logger.warn { "Invalid webhook signature from provider: $provider" }
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("Invalid signature"))
            }

            val result = integrationService.processPaymentWebhook(provider, payload)
            ResponseEntity.ok(ApiResponse.success(result, "Webhook processed successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Payment webhook processing failed for provider: $provider" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<String>("Webhook processing failed: ${e.message}"))
        }
    }

    /**
     * Retry failed payment processing
     */
    @PostMapping("/payments/{paymentId}/retry")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ATTESTATION_AUTHORITY')")
    suspend fun retryPaymentProcessing(
        @PathVariable paymentId: String,
        @RequestParam(required = false) reason: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Retrying payment processing for: $paymentId" }

            val retryResult = integrationService.retryPaymentProcessing(paymentId, reason)
            ResponseEntity.ok(ApiResponse.success(retryResult, "Payment retry initiated"))
        } catch (e: Exception) {
            logger.error(e) { "Payment retry failed for: $paymentId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Payment retry failed: ${e.message}"))
        }
    }

    // ===== BLOCKCHAIN INTEGRATIONS =====

    /**
     * Sync blockchain state with external blockchain networks
     */
    @PostMapping("/blockchain/sync")
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun syncBlockchainState(
        @RequestParam(required = false) networkId: String?,
        @RequestParam(defaultValue = "false") forceSync: Boolean
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Initiating blockchain sync for network: $networkId" }

            val syncResult = integrationService.syncBlockchainState(networkId, forceSync)
            ResponseEntity.ok(ApiResponse.success(syncResult, "Blockchain sync initiated"))
        } catch (e: Exception) {
            logger.error(e) { "Blockchain sync failed for network: $networkId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Blockchain sync failed: ${e.message}"))
        }
    }

    /**
     * Export degrees to external blockchain network
     */
    @PostMapping("/blockchain/export")
    @PreAuthorize("hasRole('ATTESTATION_AUTHORITY') or hasRole('ADMIN')")
    suspend fun exportToExternalBlockchain(
        @RequestBody @Valid request: BlockchainExportRequest
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Exporting degrees to external blockchain: ${request.targetNetwork}" }

            val exportResult = integrationService.exportToExternalBlockchain(
                certificateNumbers = request.certificateNumbers,
                targetNetwork = request.targetNetwork,
                exportFormat = request.exportFormat
            )

            ResponseEntity.ok(ApiResponse.success(exportResult, "Blockchain export initiated"))
        } catch (e: Exception) {
            logger.error(e) { "Blockchain export failed to: ${request.targetNetwork}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Export failed: ${e.message}"))
        }
    }

    // ===== THIRD-PARTY API INTEGRATIONS =====

    /**
     * Configure third-party API integration
     */
    @PostMapping("/third-party/{providerId}/configure")
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun configureThirdPartyIntegration(
        @PathVariable providerId: String,
        @RequestBody @Valid config: org.degreechain.gateway.models.IntegrationConfigRequest
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Configuring third-party integration: $providerId" }

            val configResult = integrationService.configureThirdPartyIntegration(providerId, config)
            ResponseEntity.ok(ApiResponse.success(configResult, "Integration configured successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Third-party integration configuration failed for: $providerId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Configuration failed: ${e.message}"))
        }
    }

    /**
     * Test third-party API connection
     */
    @PostMapping("/third-party/{providerId}/test")
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun testThirdPartyConnection(
        @PathVariable providerId: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            logger.info { "Testing third-party connection: $providerId" }

            val testResult = integrationService.testThirdPartyConnection(providerId)
            ResponseEntity.ok(ApiResponse.success(testResult, "Connection test completed"))
        } catch (e: Exception) {
            logger.error(e) { "Connection test failed for: $providerId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Connection test failed: ${e.message}"))
        }
    }

    // ===== INTEGRATION MONITORING =====

    /**
     * Get integration health status
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun getIntegrationHealth(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val healthStatus = integrationService.getIntegrationHealth()
            ResponseEntity.ok(ApiResponse.success(healthStatus, "Integration health retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get integration health" }
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error<Map<String, Any>>("Health check failed: ${e.message}"))
        }
    }

    /**
     * Get integration statistics and metrics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN') or hasRole('ATTESTATION_AUTHORITY')")
    suspend fun getIntegrationStatistics(
        @RequestParam(defaultValue = "7") days: Int,
        @RequestParam(required = false) provider: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val statistics = integrationService.getIntegrationStatistics(days, provider)
            ResponseEntity.ok(ApiResponse.success(statistics, "Integration statistics retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get integration statistics" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Statistics retrieval failed: ${e.message}"))
        }
    }

    /**
     * Get integration logs
     */
    @GetMapping("/logs")
    @PreAuthorize("hasRole('ADMIN')")
    suspend fun getIntegrationLogs(
        @RequestParam(defaultValue = "100") @Max(1000) limit: Int,
        @RequestParam(required = false) level: String?,
        @RequestParam(required = false) provider: String?,
        @RequestParam(required = false) startTime: String?,
        @RequestParam(required = false) endTime: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val logs = integrationService.getIntegrationLogs(limit, level, provider, startTime, endTime)
            ResponseEntity.ok(ApiResponse.success(logs, "Integration logs retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get integration logs" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Log retrieval failed: ${e.message}"))
        }
    }

    // ===== UTILITY METHODS =====

    private suspend fun validateAndGetUser(authorization: String): UserInfo {
        val token = authorization.removePrefix("Bearer ").trim()
        return authenticationService.validateToken(token)
            ?: throw SecurityException("Invalid or expired token")
    }

    @ExceptionHandler(SecurityException::class)
    fun handleSecurityException(e: SecurityException): ResponseEntity<ApiResponse<String>> {
        logger.warn { "Security exception: ${e.message}" }
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(ApiResponse.error("Authentication failed: ${e.message}"))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgumentException(e: IllegalArgumentException): ResponseEntity<ApiResponse<String>> {
        logger.warn { "Invalid argument: ${e.message}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ApiResponse.error("Invalid request: ${e.message}"))
    }
}

// ===== REQUEST/RESPONSE DATA CLASSES =====

data class AccreditationVerificationRequest(
    @field:NotBlank(message = "University code is required")
    @field:Pattern(regexp = "^[A-Z0-9]{3,10}$", message = "Invalid university code format")
    val universityCode: String,

    @field:NotBlank(message = "Country is required")
    @field:Size(min = 2, max = 3, message = "Country code must be 2-3 characters")
    val country: String,

    @field:NotBlank(message = "Registration number is required")
    val registrationNumber: String,

    val additionalInfo: Map<String, Any>? = null
)

data class GovernmentAttestationRequest(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:NotBlank(message = "Government agency is required")
    val governmentAgency: String,

    @field:NotBlank(message = "Country is required")
    val country: String,

    val additionalDocuments: List<String>? = null,
    val urgentProcessing: Boolean = false,
    val notes: String? = null
)

data class BlockchainExportRequest(
    @field:NotEmpty(message = "Certificate numbers are required")
    val certificateNumbers: List<String>,

    @field:NotBlank(message = "Target network is required")
    val targetNetwork: String,

    @field:NotBlank(message = "Export format is required")
    val exportFormat: String,

    val includeMetadata: Boolean = true,
    val encryptData: Boolean = false
)

data class IntegrationConfigRequest(
    @field:NotBlank(message = "API endpoint is required")
    val apiEndpoint: String,

    @field:NotBlank(message = "API key is required")
    val apiKey: String,

    val apiSecret: String? = null,
    val timeout: Int = 30,
    val retryAttempts: Int = 3,
    val enableLogging: Boolean = true,
    val additionalHeaders: Map<String, String>? = null,
    val configuration: Map<String, Any>? = null
)
