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
import java.util.*

/**
 * Enhanced verification controller with dual verification capabilities
 */
@RestController
@RequestMapping("/api/v1/verification")
@CrossOrigin(origins = ["*"], maxAge = 3600)
class VerificationController(
    private val enhancedDegreeService: EnhancedDegreeService,
    private val veryphyClient: VeryPhyApiClient
) {
    private val logger = LoggerFactory.getLogger(VerificationController::class.java)

    /**
     * Verify certificate using dual method (VeryPhy + Blockchain)
     */
    @PostMapping("/dual-verify")
    @PreAuthorize("hasRole('EMPLOYER') or hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun verifyWithDualMethod(
        @RequestParam("certificate") certificateFile: MultipartFile,
        @RequestParam("expectedHash", required = false) expectedHash: String?,
        @RequestParam("paymentMethod", defaultValue = "CREDIT_CARD") paymentMethod: String,
        @RequestParam("verifierOrganization") verifierOrganization: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<EnhancedVerificationResult>> {
        val startTime = System.currentTimeMillis()
        val clientIp = ApiUtils.extractClientIp(request)

        return try {
            logger.info("Processing dual verification from IP: $clientIp, Org: $verifierOrganization")

            // Validate file
            val fileValidation = FileUtils.validateCertificateFile(certificateFile)
            if (!fileValidation.isValid) {
                return ApiUtils.createErrorResponse(
                    "File validation failed: ${fileValidation.message}",
                    "INVALID_FILE"
                )
            }

            // Process verification
            val result = runBlocking {
                enhancedDegreeService.verifyDegreeWithDualMethod(certificateFile, expectedHash)
            }

            val duration = System.currentTimeMillis() - startTime
            ApiUtils.logApiCall("/verification/dual-verify", duration, result.verified)

            // Log verification attempt
            logger.info("Verification completed. Verified: ${result.verified}, Method: ${result.verificationMethod}, Confidence: ${result.confidence}, Duration: ${duration}ms")

            // TODO: Process payment if verification is successful
            if (result.verified) {
                processVerificationPayment(verifierOrganization, paymentMethod, result)
            }

            ApiUtils.createSuccessResponse(result)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Error in dual verification", e)
            ApiUtils.logApiCall("/verification/dual-verify", duration, false)
            ApiUtils.createErrorResponse("Verification failed: ${e.message}", "VERIFICATION_ERROR")
        } as ResponseEntity<ApiResponse<EnhancedVerificationResult>>
    }

    /**
     * Extract hash and OCR data from certificate (without blockchain verification)
     */
    @PostMapping("/extract-data")
    @PreAuthorize("hasRole('EMPLOYER') or hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun extractCertificateData(
        @RequestParam("certificate") certificateFile: MultipartFile,
        @RequestParam("useEnhancedExtraction", defaultValue = "true") useEnhancedExtraction: Boolean
    ): ResponseEntity<ApiResponse<VerificationExtractionResponse>> {
        return try {
            logger.info("Extracting data from certificate: ${certificateFile.originalFilename}")

            val fileValidation = FileUtils.validateCertificateFile(certificateFile)
            if (!fileValidation.isValid) {
                return ApiUtils.createErrorResponse(
                    "File validation failed: ${fileValidation.message}",
                    "INVALID_FILE"
                )
            }

            val result = veryphyClient.extractCertificateData(
                file = certificateFile,
                useEnhancedExtraction = useEnhancedExtraction
            )

            logger.info("Data extraction completed. Hash: ${result.hash}, Confidence: ${result.confidence}")
            ApiUtils.createSuccessResponse(result)

        } catch (e: Exception) {
            logger.error("Error extracting certificate data", e)
            ApiUtils.createErrorResponse("Extraction failed: ${e.message}", "EXTRACTION_ERROR")
        } as ResponseEntity<ApiResponse<VerificationExtractionResponse>>
    }

    /**
     * Batch verify multiple certificates
     */
    @PostMapping("/batch-verify")
    @PreAuthorize("hasRole('EMPLOYER') or hasRole('ADMIN')")
    fun batchVerify(
        @RequestParam("files") files: List<MultipartFile>,
        @RequestParam("verifierOrganization") verifierOrganization: String,
        @RequestParam("paymentMethod", defaultValue = "CREDIT_CARD") paymentMethod: String,
        request: HttpServletRequest
    ): ResponseEntity<ApiResponse<BatchVerificationResult>> {
        val startTime = System.currentTimeMillis()
        val clientIp = ApiUtils.extractClientIp(request)

        return try {
            logger.info("Processing batch verification from IP: $clientIp, Files: ${files.size}, Org: $verifierOrganization")

            if (files.size > 20) { // Reasonable batch limit for verification
                return ApiUtils.createErrorResponse(
                    "Batch size too large. Maximum 20 files allowed for verification",
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

            // Process batch verification
            val results = mutableListOf<EnhancedVerificationResult>()
            var successCount = 0
            var failureCount = 0

            files.forEach { file ->
                try {
                    val result = runBlocking {
                        enhancedDegreeService.verifyDegreeWithDualMethod(file)
                    }
                    results.add(result)
                    if (result.verified) {
                        successCount++
                        // Process payment for successful verification
                        processVerificationPayment(verifierOrganization, paymentMethod, result)
                    } else {
                        failureCount++
                    }
                } catch (e: Exception) {
                    logger.error("Error verifying file: ${file.originalFilename}", e)
                    failureCount++
                    results.add(
                        EnhancedVerificationResult(
                            verified = false,
                            confidence = 0.0,
                            verificationMethod = "ERROR",
                            degreeId = null,
                            certificateData = null,
                            blockchainRecord = null,
                            extractedHash = null,
                            expectedHash = null,
                            ocrMatchScore = null,
                            hashMatchScore = null,
                            message = "Error: ${e.message}",
                            verificationId = UUID.randomUUID().toString()
                        )
                    )
                }
            }

            val averageConfidence = if (results.isNotEmpty()) {
                results.filter { it.verified }.map { it.confidence }.average()
            } else 0.0

            val batchResult = BatchVerificationResult(
                batchId = UUID.randomUUID().toString(),
                totalRequests = files.size,
                successfulVerifications = successCount,
                failedVerifications = failureCount,
                results = results,
                processingTime = System.currentTimeMillis() - startTime,
                averageConfidence = averageConfidence
            )

            val duration = System.currentTimeMillis() - startTime
            ApiUtils.logApiCall("/verification/batch-verify", duration, successCount > 0)

            logger.info("Batch verification completed. Success: $successCount, Failed: $failureCount, Duration: ${duration}ms")
            ApiUtils.createSuccessResponse(batchResult)

        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error("Error in batch verification", e)
            ApiUtils.logApiCall("/verification/batch-verify", duration, false)
            ApiUtils.createErrorResponse("Batch verification failed: ${e.message}", "BATCH_ERROR")
        } as ResponseEntity<ApiResponse<BatchVerificationResult>>
    }

    /**
     * Get verification history for a specific degree
     */
    @GetMapping("/history/{degreeId}")
    @PreAuthorize("hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun getVerificationHistory(
        @PathVariable degreeId: String,
        @RequestParam("limit", defaultValue = "50") limit: Int,
        @RequestParam("offset", defaultValue = "0") offset: Int
    ): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        return try {
            logger.info("Retrieving verification history for degree: $degreeId")

            // This would typically call the blockchain service to get verification history
            // For now, we'll return a mock response
            val mockHistory = listOf(
                mapOf(
                    "verificationId" to UUID.randomUUID().toString(),
                    "verifierOrg" to "XYZ Corp",
                    "timestamp" to LocalDateTime.now().minusDays(1).toString(),
                    "method" to "HASH_AND_OCR",
                    "confidence" to 0.95,
                    "verified" to true
                ),
                mapOf(
                    "verificationId" to UUID.randomUUID().toString(),
                    "verifierOrg" to "ABC Company",
                    "timestamp" to LocalDateTime.now().minusDays(7).toString(),
                    "method" to "HASH_MATCH",
                    "confidence" to 1.0,
                    "verified" to true
                )
            )

            ApiUtils.createSuccessResponse(mockHistory)

        } catch (e: Exception) {
            logger.error("Error retrieving verification history", e)
            ApiUtils.createErrorResponse("Failed to retrieve history: ${e.message}", "HISTORY_ERROR")
        } as ResponseEntity<ApiResponse<List<Map<String, Any>>>>
    }

    /**
     * Get system-wide verification analytics
     */
    @GetMapping("/analytics")
    @PreAuthorize("hasRole('ADMIN')")
    fun getVerificationAnalytics(
        @RequestParam("days", defaultValue = "30") days: Int
    ): ResponseEntity<ApiResponse<SystemAnalytics>> {
        return try {
            logger.info("Retrieving verification analytics for last $days days")

            val analytics = runBlocking {
                enhancedDegreeService.getSystemAnalytics()
            }

            logger.info("Analytics retrieved: ${analytics.totalVerifications} total verifications")
            ApiUtils.createSuccessResponse(analytics)

        } catch (e: Exception) {
            logger.error("Error retrieving analytics", e)
            ApiUtils.createErrorResponse("Failed to retrieve analytics: ${e.message}", "ANALYTICS_ERROR")
        } as ResponseEntity<ApiResponse<SystemAnalytics>>
    }

    /**
     * Compare certificate data with expected values
     */
    @PostMapping("/compare")
    @PreAuthorize("hasRole('EMPLOYER') or hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun compareCertificateData(
        @RequestParam("certificate") certificateFile: MultipartFile,
        @RequestBody expectedData: CertificateData
    ): ResponseEntity<ApiResponse<CertificateComparisonResult>> {
        return try {
            logger.info("Comparing certificate data: ${certificateFile.originalFilename}")

            val fileValidation = FileUtils.validateCertificateFile(certificateFile)
            if (!fileValidation.isValid) {
                return ApiUtils.createErrorResponse(
                    "File validation failed: ${fileValidation.message}",
                    "INVALID_FILE"
                )
            }

            // Extract data from certificate
            val extractionResult = veryphyClient.extractCertificateData(certificateFile)
            val extractedData = extractionResult.certificateData

            if (extractedData == null) {
                return ApiUtils.createErrorResponse(
                    "Failed to extract data from certificate",
                    "EXTRACTION_FAILED"
                )
            }

            // Perform comparison
            val comparison = performCertificateComparison(extractedData, expectedData)

            logger.info("Certificate comparison completed. Overall similarity: ${comparison.overallSimilarity}")
            ApiUtils.createSuccessResponse(comparison)

        } catch (e: Exception) {
            logger.error("Error comparing certificate data", e)
            ApiUtils.createErrorResponse("Comparison failed: ${e.message}", "COMPARISON_ERROR")
        } as ResponseEntity<ApiResponse<CertificateComparisonResult>>
    }

    /**
     * Get verification pricing information
     */
    @GetMapping("/pricing")
    @PreAuthorize("hasRole('EMPLOYER') or hasRole('UNIVERSITY') or hasRole('ADMIN')")
    fun getVerificationPricing(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val pricing = mapOf(
                "singleVerification" to 10.0,
                "batchVerification" to 8.0, // Discount for batch
                "currency" to "USD",
                "paymentMethods" to listOf("CREDIT_CARD", "DEBIT_CARD", "BANK_TRANSFER", "DIGITAL_WALLET"),
                "estimatedProcessingTime" to "2-5 seconds",
                "successRate" to "99.2%"
            )

            ApiUtils.createSuccessResponse(pricing)

        } catch (e: Exception) {
            logger.error("Error retrieving pricing", e)
            ApiUtils.createErrorResponse("Failed to retrieve pricing: ${e.message}", "PRICING_ERROR")
        } as ResponseEntity<ApiResponse<Map<String, Any>>>
    }

    // ========== PRIVATE HELPER METHODS ==========

    private fun processVerificationPayment(
        verifierOrganization: String,
        paymentMethod: String,
        verificationResult: EnhancedVerificationResult
    ) {
        try {
            // TODO: Integrate with payment processor
            logger.info("Processing payment for verification: ${verificationResult.verificationId}, Org: $verifierOrganization, Method: $paymentMethod")

            // This would typically:
            // 1. Calculate fee based on verification type
            // 2. Process payment through payment gateway
            // 3. Distribute revenue to university
            // 4. Update payment records

        } catch (e: Exception) {
            logger.error("Error processing verification payment", e)
            // Don't fail verification due to payment issues, but log for follow-up
        }
    }

    private fun performCertificateComparison(
        extractedData: CertificateData,
        expectedData: CertificateData
    ): CertificateComparisonResult {
        val fieldComparisons = mutableMapOf<String, FieldComparison>()
        val discrepancies = mutableListOf<String>()
        var totalSimilarity = 0.0
        var fieldCount = 0

        // Compare each field
        val fieldsToCompare = mapOf(
            "certificateNumber" to Pair(extractedData.certificateNumber, expectedData.certificateNumber),
            "studentName" to Pair(extractedData.studentName, expectedData.studentName),
            "degreeName" to Pair(extractedData.degreeName, expectedData.degreeName),
            "institutionName" to Pair(extractedData.institutionName, expectedData.institutionName),
            "issuanceDate" to Pair(extractedData.issuanceDate, expectedData.issuanceDate),
            "grade" to Pair(extractedData.grade, expectedData.grade)
        )

        fieldsToCompare.forEach { (fieldName, values) ->
            val (extracted, expected) = values
            val similarity = calculateFieldSimilarity(extracted, expected)
            val isMatch = similarity >= 0.9

            fieldComparisons[fieldName] = FieldComparison(
                fieldName = fieldName,
                originalValue = expected,
                extractedValue = extracted,
                similarity = similarity,
                isMatch = isMatch
            )

            if (!isMatch && !extracted.isNullOrBlank() && !expected.isNullOrBlank()) {
                discrepancies.add("$fieldName: expected '$expected', found '$extracted'")
            }

            totalSimilarity += similarity
            fieldCount++
        }

        val overallSimilarity = if (fieldCount > 0) totalSimilarity / fieldCount else 0.0
        val confidence = when {
            overallSimilarity >= 0.95 -> 0.98
            overallSimilarity >= 0.90 -> 0.90
            overallSimilarity >= 0.80 -> 0.75
            overallSimilarity >= 0.70 -> 0.60
            else -> 0.40
        }

        val recommendation = when {
            overallSimilarity >= 0.95 -> "Certificate data matches expected values with high confidence"
            overallSimilarity >= 0.85 -> "Certificate data mostly matches with minor discrepancies"
            overallSimilarity >= 0.70 -> "Certificate data partially matches - manual review recommended"
            else -> "Significant discrepancies found - verification failed"
        }

        return CertificateComparisonResult(
            overallSimilarity = overallSimilarity,
            fieldMatches = fieldComparisons,
            discrepancies = discrepancies,
            confidence = confidence,
            recommendation = recommendation
        )
    }

    private fun calculateFieldSimilarity(value1: String?, value2: String?): Double {
        if (value1.isNullOrBlank() && value2.isNullOrBlank()) return 1.0
        if (value1.isNullOrBlank() || value2.isNullOrBlank()) return 0.0

        val str1 = value1.trim().lowercase()
        val str2 = value2.trim().lowercase()

        if (str1 == str2) return 1.0

        // Calculate Levenshtein distance similarity
        val longer = if (str1.length > str2.length) str1 else str2
        val shorter = if (str1.length > str2.length) str2 else str1

        if (longer.isEmpty()) return 1.0

        val editDistance = calculateEditDistance(longer, shorter)
        return (longer.length - editDistance) / longer.length.toDouble()
    }

    private fun calculateEditDistance(str1: String, str2: String): Int {
        val dp = Array(str1.length + 1) { IntArray(str2.length + 1) }

        for (i in 0..str1.length) dp[i][0] = i
        for (j in 0..str2.length) dp[0][j] = j

        for (i in 1..str1.length) {
            for (j in 1..str2.length) {
                dp[i][j] = if (str1[i - 1] == str2[j - 1]) {
                    dp[i - 1][j - 1]
                } else {
                    1 + minOf(dp[i - 1][j], dp[i][j - 1], dp[i - 1][j - 1])
                }
            }
        }

        return dp[str1.length][str2.length]
    }
}