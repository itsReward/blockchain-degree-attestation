package org.degreechain.gateway.controllers

import org.degreechain.gateway.models.*
import org.degreechain.gateway.services.EnhancedDegreeService
import org.degreechain.gateway.services.VeryPhyApiClient
import org.degreechain.gateway.utils.ApiUtils
import org.degreechain.gateway.utils.FileUtils
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.security.access.prepost.PreAuthorize
import org.slf4j.LoggerFactory
import jakarta.servlet.http.HttpServletRequest
import kotlinx.coroutines.runBlocking
import java.time.LocalDateTime
import com.fasterxml.jackson.databind.ObjectMapper
import org.degreechain.gateway.services.VeryPhyApiStatistics
import org.degreechain.gateway.services.VeryPhyHealthStatus

/**
 * Enhanced degree controller with VeryPhy integration
 */
@RestController
@RequestMapping("/api/v1/degrees")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class DegreeController(
    private val enhancedDegreeService: EnhancedDegreeService,
    private val veryphyClient: VeryPhyApiClient,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(DegreeController::class.java)

    /**
     * Submit degree with VeryPhy processing
     */
    @PostMapping("/submit-with-processing")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun submitDegreeWithProcessing(
        @RequestParam("certificate") certificateFile: MultipartFile,
        @RequestParam("studentId") studentId: String,
        @RequestParam("degreeName") degreeName: String,
        @RequestParam("institutionName") institutionName: String,
        @RequestParam("issuanceDate") issuanceDate: String,
        @RequestParam("transcripts", required = false) transcripts: String?,
        @RequestParam("enableWatermark", defaultValue = "false") enableWatermark: Boolean,
        @RequestParam("watermarkText", defaultValue = "VERIFIED") watermarkText: String,
        @RequestParam("additionalData", required = false) additionalDataJson: String?,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<EnhancedDegreeSubmissionResult>> {
        val startTime = System.currentTimeMillis()
        val clientIp = ApiUtils.extractClientIp(request)

        return try {
            logger.info("Processing degree submission from IP: $clientIp, Institution: $institutionName")

            // Validate file
            val fileValidation = FileUtils.validateCertificateFile(certificateFile)
            if (!fileValidation.isValid) {
                return ApiUtils.createErrorResponse(
                    "File validation failed: ${fileValidation.message}",
                    "INVALID_FILE"
                )
            }

            // Parse additional data
            val additionalData = if (!additionalDataJson.isNullOrBlank()) {
                try {
                    objectMapper.readValue(additionalDataJson, Map::class.java) as Map<String, Any>
                } catch (e: Exception) {
                    logger.warn("Failed to parse additional data JSON", e)
                    emptyMap()
                }
            } else {
                emptyMap()
            }

            // Create request object
            val submissionRequest = EnhancedDegreeSubmissionRequest(
                studentId = studentId,
                degreeName = degreeName,
                institutionName = institutionName,
                issuanceDate = issuanceDate,
                transcripts = transcripts,
                additionalData = additionalData,
                enableWatermark = enableWatermark,
                watermarkText = watermarkText
            )

            // Process submission
            val result = runBlocking {
                enhancedDegreeService.submitDegreeWithProcessing(certificateFile, submissionRequest)
            }

            val duration = System.currentTimeMillis() - startTime
            ApiUtils.logApiCall("/degrees/submit-with-processing", duration, result.success)

            if (result.success) {
                logger.info("Degree submission successful. Degree ID: ${result.degreeId}, Duration: ${duration}ms")
                ApiUtils.createSuccessResponse(result)
            } else {
                logger.warn("Degree submission failed: ${result.message}")
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse(success = false, data = result, message = result.message))
            }

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Error in degree submission", e)
            ApiUtils.logApiCall("/degrees/submit-with-processing", duration, false)
            ApiUtils.createErrorResponse("Submission failed: ${e.message}", "SUBMISSION_ERROR")
        }
    }

    /**
     * Get processing status for long-running operations
     */
    @GetMapping("/processing-status/{operationId}")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun getProcessingStatus(
        @PathVariable operationId: String
    ): ResponseEntity<ApiResponse<ProcessingStatus?>> {
        return try {
            val status = enhancedDegreeService.getProcessingStatus(operationId)
            if (status != null) {
                ApiUtils.createSuccessResponse(status)
            } else {
                ApiUtils.createErrorResponse("Operation not found", "OPERATION_NOT_FOUND")
            }
        } catch (e: Exception) {
            logger.error("Error getting processing status", e)
            ApiUtils.createErrorResponse("Failed to get status: ${e.message}", "STATUS_ERROR")
        }
    }

    /**
     * Batch submit multiple degrees
     */
    @PostMapping("/batch-submit")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun batchSubmitDegrees(
        @RequestParam("files") files: List<MultipartFile>,
        @RequestBody requests: List<EnhancedDegreeSubmissionRequest>,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<BatchVerificationResult>> {
        val startTime = System.currentTimeMillis()
        val clientIp = ApiUtils.extractClientIp(request)

        return try {
            logger.info("Processing batch submission from IP: $clientIp, Files: ${files.size}")

            if (files.size != requests.size) {
                return ApiUtils.createErrorResponse(
                    "Number of files (${files.size}) must match number of requests (${requests.size})",
                    "BATCH_SIZE_MISMATCH"
                )
            }

            if (files.size > 50) { // Reasonable batch limit
                return ApiUtils.createErrorResponse(
                    "Batch size too large. Maximum 50 files allowed",
                    "BATCH_TOO_LARGE"
                )
            }

            // Validate all files first
            files.forEachIndexed { index, file ->
                val validation = FileUtils.validateCertificateFile(file)
                if (!validation.isValid) {
                    return ApiUtils.createErrorResponse(
                        "File ${index + 1} validation failed: ${validation.message}",
                        "INVALID_FILE_IN_BATCH"
                    )
                }
            }

            val result = runBlocking {
                enhancedDegreeService.batchProcessDegrees(files, requests)
            }

            val duration = System.currentTimeMillis() - startTime
            ApiUtils.logApiCall("/degrees/batch-submit", duration, result.successfulVerifications > 0)

            logger.info("Batch submission completed. Success: ${result.successfulVerifications}, Failed: ${result.failedVerifications}, Duration: ${duration}ms")
            ApiUtils.createSuccessResponse(result)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Error in batch submission", e)
            ApiUtils.logApiCall("/degrees/batch-submit", duration, false)
            ApiUtils.createErrorResponse("Batch submission failed: ${e.message}", "BATCH_ERROR")
        }
    }

    /**
     * Process certificate only (for preview/testing)
     */
    @PostMapping("/process-certificate")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun processCertificateOnly(
        @RequestParam("certificate") certificateFile: MultipartFile,
        @RequestParam("embedHash", defaultValue = "true") embedHash: Boolean,
        @RequestParam("addWatermark", defaultValue = "false") addWatermark: Boolean,
        @RequestParam("watermarkText", defaultValue = "VERIFIED") watermarkText: String
    ): ResponseEntity<ApiResponse<ProcessedCertificateResponse>> {
        return try {
            logger.info("Processing certificate for preview: ${certificateFile.originalFilename}")

            val fileValidation = FileUtils.validateCertificateFile(certificateFile)
            if (!fileValidation.isValid) {
                return ApiUtils.createErrorResponse(
                    "File validation failed: ${fileValidation.message}",
                    "INVALID_FILE"
                )
            }

            val result = veryphyClient.processCertificate(
                file = certificateFile,
                embedHash = embedHash,
                addWatermark = addWatermark,
                watermarkText = watermarkText
            )

            logger.info("Certificate processed successfully. Hash: ${result.hash}")
            ApiUtils.createSuccessResponse(result)

        } catch (e: Exception) {
            logger.error("Error processing certificate", e)
            ApiUtils.createErrorResponse("Processing failed: ${e.message}", "PROCESSING_ERROR")
        }
    }

    /**
     * Get degree by ID with enhanced information
     */
    @GetMapping("/{degreeId}")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('EMPLOYER') or hasRole('ADMIN')")
    fun getDegree(
        @PathVariable degreeId: String
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            logger.info("Retrieving degree: $degreeId")

            // This would typically call a service to get degree details
            // For now, we'll return a placeholder response
            val degreeInfo = mapOf(
                "degreeId" to degreeId,
                "status" to "active",
                "retrievedAt" to LocalDateTime.now().toString()
            )

            ApiUtils.createSuccessResponse(degreeInfo)

        } catch (e: Exception) {
            logger.error("Error retrieving degree", e)
            ApiUtils.createErrorResponse("Failed to retrieve degree: ${e.message}", "RETRIEVAL_ERROR")
        }
    }

    /**
     * Get degree by certificate hash
     */
    @GetMapping("/by-hash/{certificateHash}")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('EMPLOYER') or hasRole('ADMIN')")
    fun getDegreeByHash(
        @PathVariable certificateHash: String
    ): ResponseEntity<ApiResponse<Any>> {
        return try {
            logger.info("Retrieving degree by hash: $certificateHash")

            // This would call the blockchain service
            // For now, we'll return a placeholder response
            val degreeInfo = mapOf(
                "certificateHash" to certificateHash,
                "found" to true,
                "retrievedAt" to LocalDateTime.now().toString()
            )

            ApiUtils.createSuccessResponse(degreeInfo)

        } catch (e: Exception) {
            logger.error("Error retrieving degree by hash", e)
            ApiUtils.createErrorResponse("Failed to retrieve degree: ${e.message}", "RETRIEVAL_ERROR")
        }
    }

    /**
     * Health check for VeryPhy integration
     */
    @GetMapping("/health/veryphy")
    @PreAuthorize("hasRole('ADMIN')")
    fun checkVeryPhyHealth(): ResponseEntity<ApiResponse<VeryPhyHealthStatus>> {
        return try {
            val healthStatus = veryphyClient.checkApiHealth()
            logger.info("VeryPhy health check: ${healthStatus.status}")

            if (healthStatus.isHealthy) {
                ApiUtils.createSuccessResponse(healthStatus)
            } else {
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse(success = false, data = healthStatus, message = "VeryPhy API is unhealthy"))
            }

        } catch (e: Exception) {
            logger.error("Error checking VeryPhy health", e)
            ApiUtils.createErrorResponse("Health check failed: ${e.message}", "HEALTH_CHECK_ERROR")
        }
    }

    /**
     * Get VeryPhy API statistics
     */
    @GetMapping("/stats/veryphy")
    @PreAuthorize("hasRole('ADMIN')")
    fun getVeryPhyStatistics(): ResponseEntity<ApiResponse<VeryPhyApiStatistics>> {
        return try {
            val stats = veryphyClient.getApiStatistics()
            logger.info("Retrieved VeryPhy statistics")
            ApiUtils.createSuccessResponse(stats)

        } catch (e: Exception) {
            logger.error("Error getting VeryPhy statistics", e)
            ApiUtils.createErrorResponse("Failed to get statistics: ${e.message}", "STATS_ERROR")
        }
    }
}

/**
 * Generic API response wrapper
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val error: String? = null
)