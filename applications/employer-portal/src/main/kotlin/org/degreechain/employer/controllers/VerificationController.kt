package org.degreechain.employer.controllers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.common.models.VerificationStatus
import org.degreechain.employer.models.VerificationRequest
import org.degreechain.employer.models.VerificationResult
import org.degreechain.employer.services.VerificationService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Controller for handling degree verification operations in the employer portal
 * Manages single and batch verifications, verification history, and analytics
 */
@RestController
@RequestMapping("/api/v1/verifications")
@PreAuthorize("hasRole('EMPLOYER')")
@Validated
class VerificationController(
    private val verificationService: VerificationService
) {

    /**
     * Verify a single degree certificate
     */
    @PostMapping("/verify")
    suspend fun verifyDegree(
        @Valid @RequestBody request: VerificationRequest
    ): ResponseEntity<ApiResponse<VerificationResult>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Processing degree verification for certificate: ${request.certificateNumber}" }

            val verificationResult = verificationService.verifyDegree(request)

            ResponseEntity.ok(
                ApiResponse.success(
                    verificationResult,
                    "Degree verification completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to verify degree for certificate: ${request.certificateNumber}" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<VerificationResult>("Verification failed: ${e.message}"))
        }
    }

    /**
     * Verify multiple degrees in batch
     */
    @PostMapping("/batch-verify")
    suspend fun batchVerifyDegrees(
        @Valid @RequestBody request: BatchVerificationRequest
    ): ResponseEntity<ApiResponse<BatchVerificationResult>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Processing batch verification for ${request.verificationRequests.size} certificates" }

            val batchResult = verificationService.batchVerifyDegrees(request)

            ResponseEntity.ok(
                ApiResponse.success(
                    batchResult,
                    "Batch verification completed"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process batch verification" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<BatchVerificationResult>("Batch verification failed: ${e.message}"))
        }
    }

    /**
     * Upload and verify degrees from CSV/Excel file
     */
    @PostMapping("/upload-verify", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun uploadAndVerifyDegrees(
        @RequestParam("file") file: MultipartFile,
        @RequestParam @NotBlank organizationName: String,
        @RequestParam @NotBlank verifierEmail: String,
        @RequestParam @NotBlank paymentMethod: String,
        @RequestParam @Min(1) paymentAmount: Double
    ): ResponseEntity<ApiResponse<BatchVerificationResult>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Processing file upload verification for organization: $organizationName" }

            val batchResult = verificationService.uploadAndVerifyDegrees(
                file = file,
                organizationName = organizationName,
                verifierEmail = verifierEmail,
                paymentMethod = paymentMethod,
                paymentAmount = paymentAmount
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    batchResult,
                    "File upload verification completed"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to process file upload verification" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<BatchVerificationResult>("File verification failed: ${e.message}"))
        }
    }

    /**
     * Get verification result by ID
     */
    @GetMapping("/{verificationId}")
    suspend fun getVerificationResult(
        @PathVariable @NotBlank verificationId: String
    ): ResponseEntity<ApiResponse<VerificationResult>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving verification result: $verificationId" }

            val verificationResult = verificationService.getVerificationResult(verificationId)
            ResponseEntity.ok(
                ApiResponse.success(
                    verificationResult,
                    "Verification result retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification result: $verificationId" }
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error<VerificationResult>("Verification result not found"))
        }
    }

    /**
     * Get verification history for an organization
     */
    @GetMapping("/history")
    suspend fun getVerificationHistory(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @RequestParam(required = false) status: VerificationStatus?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(required = false) certificateNumber: String?
    ): ResponseEntity<ApiResponse<List<VerificationResult>>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving verification history for organization: $organizationName" }

            val verificationHistory = verificationService.getVerificationHistory(
                organizationName = organizationName,
                page = page,
                size = size,
                status = status,
                startDate = startDate,
                endDate = endDate,
                certificateNumber = certificateNumber
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    verificationHistory,
                    "Verification history retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification history for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<List<VerificationResult>>("Failed to retrieve verification history"))
        }
    }

    /**
     * Get verification analytics for an organization
     */
    @GetMapping("/analytics")
    suspend fun getVerificationAnalytics(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving verification analytics for organization: $organizationName" }

            val analytics = verificationService.getVerificationAnalytics(
                organizationName = organizationName,
                startDate = startDate,
                endDate = endDate
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    analytics,
                    "Verification analytics retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification analytics for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Failed to retrieve verification analytics"))
        }
    }

    /**
     * Download verification report
     */
    @GetMapping("/report/download")
    suspend fun downloadVerificationReport(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?,
        @RequestParam(defaultValue = "csv") @Pattern(regexp = "^(csv|excel|pdf)$") format: String
    ): ResponseEntity<ByteArray> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Generating verification report for organization: $organizationName in format: $format" }

            val reportData = verificationService.downloadVerificationReport(
                organizationName = organizationName,
                startDate = LocalDateTime.parse(startDate ?: "2020-01-01T00:00:00"),
                endDate = LocalDateTime.parse(endDate ?: LocalDateTime.now().toString())
            )

            val contentType = when (format.lowercase()) {
                "csv" -> "text/csv"
                "excel" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "pdf" -> "application/pdf"
                else -> "text/csv"
            }

            val filename = "verification-report-${organizationName}-${LocalDateTime.now()}.${format.lowercase()}"

            ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$filename\"")
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .body(reportData)
        } catch (e: Exception) {
            logger.error(e) { "Failed to generate verification report for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .body("Failed to generate report: ${e.message}".toByteArray())
        }
    }

    /**
     * Re-verify a degree (e.g., if original verification was inconclusive)
     */
    @PostMapping("/{verificationId}/re-verify")
    suspend fun reVerifyDegree(
        @PathVariable @NotBlank verificationId: String,
        @Valid @RequestBody request: ReVerificationRequest
    ): ResponseEntity<ApiResponse<VerificationResult>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Re-verifying degree for verification ID: $verificationId" }

            val verificationResult = verificationService.reVerifyDegree(
                verificationId = verificationId,
                enhancedExtraction = request.enhancedExtraction,
                additionalNotes = request.additionalNotes
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    verificationResult,
                    "Degree re-verification completed successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to re-verify degree for verification ID: $verificationId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<VerificationResult>("Re-verification failed: ${e.message}"))
        }
    }

    /**
     * Get verification cost estimate
     */
    @PostMapping("/estimate-cost")
    suspend fun estimateVerificationCost(
        @Valid @RequestBody request: CostEstimateRequest
    ): ResponseEntity<ApiResponse<VerificationCostEstimate>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Estimating verification cost for ${request.certificateCount} certificates" }

            val costEstimate = verificationService.estimateVerificationCost(
                certificateCount = request.certificateCount,
                paymentMethod = request.paymentMethod,
                bulkDiscount = request.bulkDiscount
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    costEstimate,
                    "Verification cost estimated successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to estimate verification cost" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<VerificationCostEstimate>("Cost estimation failed: ${e.message}"))
        }
    }

    /**
     * Get verification statistics for dashboard
     */
    @GetMapping("/dashboard/stats")
    suspend fun getDashboardStats(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(defaultValue = "30") @Min(1) days: Int
    ): ResponseEntity<ApiResponse<Map<String, Any>>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving dashboard stats for organization: $organizationName (last $days days)" }

            val stats = verificationService.getDashboardStats(
                organizationName = organizationName,
                days = days
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    stats,
                    "Dashboard statistics retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get dashboard stats for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Failed to retrieve dashboard statistics"))
        }
    }
}

/**
 * Request model for batch verification
 */
data class BatchVerificationRequest(
    @field:Valid
    val verificationRequests: List<VerificationRequest>,

    val continueOnError: Boolean = true,
    val maxConcurrentVerifications: Int = 10
) {
    init {
        require(verificationRequests.isNotEmpty()) { "At least one verification request is required" }
        require(verificationRequests.size <= 100) { "Maximum 100 verifications per batch" }
    }
}

/**
 * Result model for batch verification
 */
data class BatchVerificationResult(
    val totalRequested: Int,
    val successfulVerifications: Int,
    val failedVerifications: Int,
    val totalCost: Double,
    val processingTime: Long, // milliseconds
    val results: List<VerificationResult>,
    val errors: List<BatchVerificationError> = emptyList()
)

/**
 * Error information for batch verification
 */
data class BatchVerificationError(
    val certificateNumber: String,
    val errorMessage: String,
    val errorCode: String
)

/**
 * Request model for re-verification
 */
data class ReVerificationRequest(
    val enhancedExtraction: Boolean = true,
    val additionalNotes: String? = null
)

/**
 * Request model for cost estimation
 */
data class CostEstimateRequest(
    @field:Min(value = 1, message = "Certificate count must be positive")
    val certificateCount: Int,

    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "^(CREDIT_CARD|BANK_TRANSFER|CRYPTO)$",
        message = "Invalid payment method"
    )
    val paymentMethod: String,

    val bulkDiscount: Boolean = true
)

/**
 * Cost estimate result
 */
data class VerificationCostEstimate(
    val totalCertificates: Int,
    val costPerVerification: Double,
    val bulkDiscount: Double,
    val totalCost: Double,
    val estimatedProcessingTime: String,
    val paymentMethodFees: Map<String, Double>,
    val breakdown: Map<String, Any>
)