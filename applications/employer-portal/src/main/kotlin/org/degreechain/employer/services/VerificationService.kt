package org.degreechain.employer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.models.VerificationStatus
import org.degreechain.common.utils.ValidationUtils
import org.degreechain.employer.models.VerificationRequest
import org.degreechain.employer.models.VerificationResult
import org.degreechain.employer.controllers.BatchVerificationRequest
import org.degreechain.employer.controllers.BatchVerificationResult
import org.degreechain.employer.controllers.BatchVerificationError
import org.degreechain.employer.controllers.VerificationCostEstimate
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.max

private val logger = KotlinLogging.logger {}

@Service
class VerificationService(
    private val contractInvoker: ContractInvoker,
    private val paymentService: PaymentService
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    val random = java.util.Random()

    private fun safeMapOf(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        return pairs.mapNotNull { (key, value) ->
            value?.let { key to it }
        }.toMap()
    }


    suspend fun verifyDegree(request: VerificationRequest): VerificationResult = withContext(Dispatchers.IO) {
        logger.info { "Starting degree verification for certificate: ${request.certificateNumber}" }

        try {
            // Validate request
            validateVerificationRequest(request)

            // Process payment first
            val paymentResult = paymentService.processVerificationPayment(
                amount = request.paymentAmount,
                paymentMethod = request.paymentMethod,
                certificateNumber = request.certificateNumber,
                organizationName = request.verifierOrganization
            )

            if (!paymentResult.success) {
                throw BusinessException(
                    "Payment failed: ${paymentResult.errorMessage}",
                    ErrorCode.PAYMENT_FAILED
                )
            }

            // Perform verification on blockchain
            val verificationJson = contractInvoker.verifyDegree(
                certificateNumber = request.certificateNumber,
                verifierOrganization = request.verifierOrganization,
                verifierEmail = request.verifierEmail,
                providedHash = request.providedHash
            )

            val verificationData: Map<String, Any> = objectMapper.readValue(verificationJson)

            // Process payment on blockchain
            contractInvoker.processVerificationPayment(
                certificateNumber = request.certificateNumber,
                verifierOrganization = request.verifierOrganization,
                verifierEmail = request.verifierEmail,
                paymentAmount = request.paymentAmount
            )

            // Create verification result
            val result = VerificationResult(
                verificationId = UUID.randomUUID().toString(),
                certificateNumber = request.certificateNumber,
                verificationStatus = mapVerificationStatus(verificationData["verificationResult"] as String),
                confidence = (verificationData["confidence"] as Number).toDouble(),
                studentName = verificationData["studentName"] as? String,
                degreeName = verificationData["degreeName"] as? String,
                facultyName = verificationData["facultyName"] as? String,
                degreeClassification = verificationData["degreeClassification"] as? String,
                issuanceDate = verificationData["issuanceDate"] as? String,
                universityName = verificationData["universityName"] as? String,
                universityCode = verificationData["universityCode"] as? String,
                verifierOrganization = request.verifierOrganization,
                verifierEmail = request.verifierEmail,
                verificationTimestamp = LocalDateTime.now(),
                paymentAmount = request.paymentAmount,
                paymentId = paymentResult.paymentId,
                extractionMethod = verificationData["extractionMethod"] as? String
            )

            logger.info { "Degree verification completed successfully for certificate: ${request.certificateNumber}" }
            result

        } catch (e: Exception) {
            logger.error(e) { "Degree verification failed for certificate: ${request.certificateNumber}" }
            throw BusinessException(
                "Verification failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Batch verify multiple degrees
     */
    suspend fun batchVerifyDegrees(request: BatchVerificationRequest): BatchVerificationResult = withContext(Dispatchers.IO) {
        logger.info { "Starting batch verification for ${request.verificationRequests.size} certificates" }

        val startTime = System.currentTimeMillis()
        val results = mutableListOf<VerificationResult>()
        val errors = mutableListOf<BatchVerificationError>()
        var totalCost = 0.0

        try {
            // Process verifications with limited concurrency
            val chunks = request.verificationRequests.chunked(request.maxConcurrentVerifications)

            for (chunk in chunks) {
                val chunkResults = chunk.map { verificationRequest ->
                    async {
                        try {
                            val result = verifyDegree(verificationRequest)
                            totalCost += result.paymentAmount
                            result
                        } catch (e: Exception) {
                            logger.error(e) { "Failed to verify certificate: ${verificationRequest.certificateNumber}" }
                            errors.add(
                                BatchVerificationError(
                                    certificateNumber = verificationRequest.certificateNumber,
                                    errorMessage = e.message ?: "Unknown error",
                                    errorCode = when (e) {
                                        is BusinessException -> e.errorCode.name
                                        else -> ErrorCode.INTERNAL_SERVER_ERROR.name
                                    }
                                )
                            )

                            if (!request.continueOnError) {
                                throw e
                            }
                            null
                        }
                    }
                }.awaitAll().filterNotNull()

                results.addAll(chunkResults)

                if (!request.continueOnError && errors.isNotEmpty()) {
                    break
                }
            }

            val processingTime = System.currentTimeMillis() - startTime

            BatchVerificationResult(
                totalRequested = request.verificationRequests.size,
                successfulVerifications = results.size,
                failedVerifications = errors.size,
                totalCost = totalCost,
                processingTime = processingTime,
                results = results,
                errors = errors
            )

        } catch (e: Exception) {
            logger.error(e) { "Batch verification failed" }
            throw BusinessException(
                "Batch verification failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Process file upload verification
     */
    suspend fun processFileUploadVerification(
        file: MultipartFile,
        organizationName: String,
        paymentMethod: String
    ): BatchVerificationResult = withContext(Dispatchers.IO) {
        logger.info { "Processing file upload verification for organization: $organizationName" }

        try {
            // Validate file
            if (file.isEmpty) {
                throw BusinessException("File is empty", ErrorCode.VALIDATION_ERROR)
            }

            val allowedTypes = setOf("text/csv", "application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            if (file.contentType !in allowedTypes) {
                throw BusinessException(
                    "Unsupported file type. Only CSV and Excel files are supported.",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            // Parse file and extract verification requests
            val verificationRequests = parseVerificationFile(file, organizationName, paymentMethod)

            // Process batch verification
            val batchRequest = BatchVerificationRequest(
                verificationRequests = verificationRequests,
                continueOnError = true,
                maxConcurrentVerifications = 5
            )

            batchVerifyDegrees(batchRequest)

        } catch (e: Exception) {
            logger.error(e) { "File upload verification failed" }
            throw BusinessException(
                "File verification failed: ${e.message}",
                ErrorCode.FILE_PROCESSING_ERROR,
                cause = e
            )
        }
    }

    /**
     * Get verification result by ID
     */
    suspend fun getVerificationResult(verificationId: String): VerificationResult = withContext(Dispatchers.IO) {
        logger.info { "Retrieving verification result: $verificationId" }

        try {
            ValidationUtils.validateRequired(verificationId, "Verification ID")

            // In a real implementation, this would query the database
            // For now, return a mock result
            VerificationResult(
                verificationId = verificationId,
                certificateNumber = "CERT-${UUID.randomUUID().toString().substring(0, 8)}",
                verificationStatus = VerificationStatus.VERIFIED,
                confidence = 0.95,
                studentName = "John Doe",
                degreeName = "Bachelor of Science in Computer Science",
                facultyName = "Faculty of Engineering",
                degreeClassification = "First Class Honours",
                issuanceDate = "2023-06-15",
                universityName = "University of Technology",
                universityCode = "UNITECH",
                verifierOrganization = "TechCorp",
                verifierEmail = "hr@techcorp.com",
                verificationTimestamp = LocalDateTime.now(),
                paymentAmount = 10.0,
                paymentId = "PAY-${UUID.randomUUID().toString().substring(0, 8)}",
                extractionMethod = "BLOCKCHAIN_VERIFICATION"
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification result: $verificationId" }
            throw BusinessException(
                "Verification result not found",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    /**
     * Get verification history with filtering
     */
    suspend fun getVerificationHistory(
        organizationName: String,
        page: Int,
        size: Int,
        status: VerificationStatus?,
        startDate: String?,
        endDate: String?,
        certificateNumber: String?
    ): List<VerificationResult> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving verification history for organization: $organizationName" }

        try {
            ValidationUtils.validateRequired(organizationName, "Organization Name")

            // Generate mock history data
            val mockHistory = generateMockVerificationHistory(organizationName, status)

            // Apply filters
            var filteredHistory = mockHistory.toList()

            if (!certificateNumber.isNullOrBlank()) {
                filteredHistory = filteredHistory.filter {
                    it.certificateNumber.contains(certificateNumber, ignoreCase = true)
                }
            }

            if (!startDate.isNullOrBlank()) {
                val start = LocalDateTime.parse(startDate)
                filteredHistory = filteredHistory.filter { it.verificationTimestamp.isAfter(start) }
            }

            if (!endDate.isNullOrBlank()) {
                val end = LocalDateTime.parse(endDate)
                filteredHistory = filteredHistory.filter { it.verificationTimestamp.isBefore(end) }
            }

            // Apply pagination
            val offset = page * size
            filteredHistory.drop(offset).take(size)

        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification history for: $organizationName" }
            throw BusinessException(
                "Failed to retrieve verification history",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Get verification analytics
     */
    suspend fun getVerificationAnalytics(
        organizationName: String,
        startDate: String?,
        endDate: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving verification analytics for organization: $organizationName" }

        try {
            ValidationUtils.validateRequired(organizationName, "Organization Name")

            val mockHistory = generateMockVerificationHistory(organizationName, null)

            // Calculate analytics
            val totalVerifications = mockHistory.size
            val successfulVerifications = mockHistory.count { it.verificationStatus == VerificationStatus.VERIFIED }
            val failedVerifications = mockHistory.count { it.verificationStatus == VerificationStatus.FAILED }
            val pendingVerifications = mockHistory.count { it.verificationStatus == VerificationStatus.PENDING }

            val successRate = if (totalVerifications > 0) {
                (successfulVerifications.toDouble() / totalVerifications) * 100
            } else 0.0

            val totalSpent = mockHistory.sumOf { it.paymentAmount }
            val averageConfidence = mockHistory.map { it.confidence }.average()

            // Group by university
            val universityBreakdown = mockHistory.groupBy { it.universityName ?: "Unknown" }
                .mapValues { (_, verifications) ->
                    mapOf(
                        "count" to verifications.size,
                        "successRate" to if (verifications.isNotEmpty()) {
                            (verifications.count { it.verificationStatus == VerificationStatus.VERIFIED }.toDouble() / verifications.size) * 100
                        } else 0.0
                    )
                }

            // Trend data (last 30 days)
            val now = LocalDateTime.now()
            val trendData = (0..29).map { daysAgo ->
                val date = now.minusDays(daysAgo.toLong())
                val dayVerifications = mockHistory.filter {
                    it.verificationTimestamp.toLocalDate() == date.toLocalDate()
                }
                mapOf(
                    "date" to date.toLocalDate().toString(),
                    "verifications" to dayVerifications.size,
                    "successfulVerifications" to dayVerifications.count { it.verificationStatus == VerificationStatus.VERIFIED }
                )
            }.reversed()

            mapOf(
                "summary" to mapOf(
                    "totalVerifications" to totalVerifications,
                    "successfulVerifications" to successfulVerifications,
                    "failedVerifications" to failedVerifications,
                    "pendingVerifications" to pendingVerifications,
                    "successRate" to successRate,
                    "totalAmountSpent" to totalSpent,
                    "averageConfidence" to averageConfidence,
                    "averageCostPerVerification" to if (totalVerifications > 0) totalSpent / totalVerifications else 0.0
                ),
                "universityBreakdown" to universityBreakdown,
                "trendData" to trendData,
                "degreeTypeDistribution" to mockHistory.groupBy { it.degreeName ?: "Unknown" }
                    .mapValues { it.value.size },
                "confidenceDistribution" to mapOf(
                    "high" to mockHistory.count { it.confidence >= 0.9 },
                    "medium" to mockHistory.count { it.confidence in 0.7..0.89 },
                    "low" to mockHistory.count { it.confidence < 0.7 }
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get verification analytics for: $organizationName" }
            throw BusinessException(
                "Failed to retrieve verification analytics",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Re-verify a degree
     */
    suspend fun reVerifyDegree(
        verificationId: String,
        enhancedExtraction: Boolean,
        additionalNotes: String?
    ): VerificationResult = withContext(Dispatchers.IO) {
        logger.info { "Re-verifying degree for verification ID: $verificationId" }

        try {
            ValidationUtils.validateRequired(verificationId, "Verification ID")

            // Get original verification
            val originalVerification = getVerificationResult(verificationId)

            // Create new verification request based on original
            val reVerificationRequest = VerificationRequest(
                certificateNumber = originalVerification.certificateNumber,
                verifierOrganization = originalVerification.verifierOrganization,
                verifierEmail = originalVerification.verifierEmail,
                paymentAmount = originalVerification.paymentAmount * 0.5, // Re-verification discount
                paymentMethod = "CREDIT_CARD", // Default method for re-verification
                providedHash = null,
                additionalNotes = additionalNotes
            )

            // Perform re-verification
            val reVerificationResult = verifyDegree(reVerificationRequest)

            logger.info { "Re-verification completed for verification ID: $verificationId" }
            reVerificationResult

        } catch (e: Exception) {
            logger.error(e) { "Failed to re-verify degree for verification ID: $verificationId" }
            throw BusinessException(
                "Re-verification failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Estimate verification cost
     */
    suspend fun estimateVerificationCost(
        certificateCount: Int,
        paymentMethod: String,
        bulkDiscount: Boolean
    ): VerificationCostEstimate = withContext(Dispatchers.IO) {
        logger.info { "Estimating verification cost for $certificateCount certificates" }

        try {
            val baseCostPerVerification = 10.0

            // Calculate bulk discount
            val bulkDiscountRate = if (bulkDiscount && certificateCount >= 10) {
                when {
                    certificateCount >= 100 -> 0.25
                    certificateCount >= 50 -> 0.20
                    certificateCount >= 25 -> 0.15
                    else -> 0.10
                }
            } else 0.0

            val discountedCostPerVerification = baseCostPerVerification * (1 - bulkDiscountRate)
            val subtotal = discountedCostPerVerification * certificateCount
            val bulkDiscountAmount = baseCostPerVerification * certificateCount * bulkDiscountRate

            // Payment method fees
            val paymentMethodFees = mapOf(
                "CREDIT_CARD" to subtotal * 0.029, // 2.9% fee
                "BANK_TRANSFER" to max(2.0, subtotal * 0.01), // $2 minimum or 1%
                "CRYPTO" to subtotal * 0.015 // 1.5% fee
            )

            val paymentFee = paymentMethodFees[paymentMethod] ?: 0.0
            val totalCost = subtotal + paymentFee

            // Estimated processing time
            val estimatedProcessingTime = when {
                certificateCount <= 10 -> "5-10 minutes"
                certificateCount <= 50 -> "30-60 minutes"
                certificateCount <= 100 -> "2-4 hours"
                else -> "4-8 hours"
            }

            VerificationCostEstimate(
                totalCertificates = certificateCount,
                costPerVerification = discountedCostPerVerification,
                bulkDiscount = bulkDiscountAmount,
                totalCost = totalCost,
                estimatedProcessingTime = estimatedProcessingTime,
                paymentMethodFees = paymentMethodFees,
                breakdown = mapOf(
                    "baseCost" to baseCostPerVerification * certificateCount,
                    "bulkDiscountRate" to bulkDiscountRate,
                    "subtotal" to subtotal,
                    "paymentFee" to paymentFee,
                    "finalTotal" to totalCost
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to estimate verification cost" }
            throw BusinessException(
                "Cost estimation failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Get dashboard statistics
     */
    suspend fun getDashboardStats(
        organizationName: String,
        days: Int
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving dashboard stats for organization: $organizationName (last $days days)" }

        try {
            ValidationUtils.validateRequired(organizationName, "Organization Name")

            val cutoffDate = LocalDateTime.now().minusDays(days.toLong())
            val mockHistory = generateMockVerificationHistory(organizationName, null)
                .filter { it.verificationTimestamp.isAfter(cutoffDate) }

            val totalVerifications = mockHistory.size
            val successfulVerifications = mockHistory.count { it.verificationStatus == VerificationStatus.VERIFIED }
            val pendingVerifications = mockHistory.count { it.verificationStatus == VerificationStatus.PENDING }
            val totalSpent = mockHistory.sumOf { it.paymentAmount }

            // Recent activity (last 7 days)
            val recentActivity = (0..6).map { daysAgo ->
                val date = LocalDateTime.now().minusDays(daysAgo.toLong())
                val dayVerifications = mockHistory.filter {
                    it.verificationTimestamp.toLocalDate() == date.toLocalDate()
                }
                mapOf(
                    "date" to date.toLocalDate().toString(),
                    "count" to dayVerifications.size
                )
            }.reversed()

            // Top universities
            val topUniversities = mockHistory.groupBy { it.universityName ?: "Unknown" }
                .mapValues { it.value.size }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { mapOf("university" to it.first, "count" to it.second) }

            mapOf(
                "totalVerifications" to totalVerifications,
                "successfulVerifications" to successfulVerifications,
                "pendingVerifications" to pendingVerifications,
                "successRate" to if (totalVerifications > 0) (successfulVerifications.toDouble() / totalVerifications) * 100 else 0.0,
                "totalSpent" to totalSpent,
                "averageCostPerVerification" to if (totalVerifications > 0) totalSpent / totalVerifications else 0.0,
                "recentActivity" to recentActivity,
                "topUniversities" to topUniversities,
                "lastVerificationDate" to mockHistory.maxByOrNull { it.verificationTimestamp }?.verificationTimestamp?.toString(),
                "period" to "${days} days"
            ) as Map<String, Any>

        } catch (e: Exception) {
            logger.error(e) { "Failed to get dashboard stats for: $organizationName" }
            throw BusinessException(
                "Failed to retrieve dashboard statistics",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
            // This line is never reached due to throw
            emptyMap<String, Any>() // But would fix the type mismatch if reached
        }
    }

    suspend fun downloadVerificationReport(
        organizationName: String,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): ByteArray = withContext(Dispatchers.IO) {
        logger.info { "Generating verification report for organization: $organizationName" }

        // In a real implementation, this would generate a proper report (PDF/Excel)
        // For now, return a simple CSV format as bytes
        val history = generateMockVerificationHistory(organizationName, null)
        val csvContent = generateCsvReport(history, startDate, endDate)

        csvContent.toByteArray()
    }

    // Helper methods

    private fun parseVerificationFile(
        file: MultipartFile,
        organizationName: String,
        paymentMethod: String
    ): List<VerificationRequest> {
        val content = String(file.bytes)
        val lines = content.split("\n").filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw BusinessException("File is empty or invalid", ErrorCode.VALIDATION_ERROR)
        }

        // Assume CSV format: certificateNumber,verifierEmail,paymentAmount
        val verificationRequests = mutableListOf<VerificationRequest>()

        lines.drop(1).forEachIndexed { index, line ->
            try {
                val parts = line.split(",").map { it.trim() }
                if (parts.size >= 3) {
                    verificationRequests.add(
                        VerificationRequest(
                            certificateNumber = parts[0],
                            verifierOrganization = organizationName,
                            verifierEmail = parts[1],
                            paymentAmount = parts[2].toDouble(),
                            paymentMethod = paymentMethod
                        )
                    )
                }
            } catch (e: Exception) {
                logger.warn { "Failed to parse line ${index + 1}: $line" }
            }
        }

        if (verificationRequests.isEmpty()) {
            throw BusinessException("No valid verification requests found in file", ErrorCode.VALIDATION_ERROR)
        }

        return verificationRequests
    }

    private fun generateMockVerificationHistory(
        organizationName: String,
        status: VerificationStatus?
    ): List<VerificationResult> {
        val statuses = listOf(VerificationStatus.VERIFIED, VerificationStatus.FAILED, VerificationStatus.PENDING)
        val universities = listOf("University of Technology", "State University", "Technical College", "Business School")
        val degrees = listOf("Bachelor of Science", "Master of Business Administration", "Doctor of Philosophy", "Bachelor of Arts")

        return (1..50).map { i ->
            val verificationStatus = status ?: statuses.random()
            VerificationResult(
                verificationId = "VER-${organizationName}-${i.toString().padStart(4, '0')}",
                certificateNumber = "CERT-${i.toString().padStart(6, '0')}",
                verificationStatus = verificationStatus,
                confidence = when (verificationStatus) {
                    VerificationStatus.VERIFIED -> 0.8 + random.nextDouble() * 0.2
                    VerificationStatus.FAILED -> random.nextDouble() * 0.5
                    else -> 0.5 + random.nextDouble() * 0.3
                },
                studentName = "Student $i",
                degreeName = degrees.random(),
                facultyName = "Faculty of ${listOf("Engineering", "Business", "Arts", "Science").random()}",
                degreeClassification = listOf("First Class", "Second Class Upper", "Second Class Lower", "Third Class").random(),
                issuanceDate = "202${(0..3).random()}-${(1..12).random().toString().padStart(2, '0')}-${(1..28).random().toString().padStart(2, '0')}",
                universityName = universities.random(),
                universityCode = "UNI${(100..999).random()}",
                verifierOrganization = organizationName,
                verifierEmail = "verifier$i@$organizationName.com",
                verificationTimestamp = LocalDateTime.now().minusDays((0..60).random().toLong()),
                paymentAmount = 5.0 + random.nextDouble() * 15.0,
                paymentId = "PAY-${UUID.randomUUID().toString().substring(0, 8)}",
                extractionMethod = listOf("BLOCKCHAIN_VERIFICATION", "OCR_EXTRACTION", "MANUAL_VERIFICATION").random()
            )
        }
    }

    private fun generateCsvReport(
        history: List<VerificationResult>,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): String {
        val filteredHistory = history.filter { verification ->
            val timestamp = verification.verificationTimestamp
            (startDate == null || timestamp.isAfter(startDate)) &&
                    (endDate == null || timestamp.isBefore(endDate))
        }

        val csv = StringBuilder()
        csv.appendLine("Verification ID,Certificate Number,Status,Confidence,Student Name,Degree Name,University,Verification Date,Payment Amount")

        filteredHistory.forEach { verification ->
            csv.appendLine(
                "${verification.verificationId},${verification.certificateNumber},${verification.verificationStatus}," +
                        "${verification.confidence},${verification.studentName},${verification.degreeName}," +
                        "${verification.universityName},${verification.verificationTimestamp},${verification.paymentAmount}"
            )
        }

        return csv.toString()
    }

    private fun validateVerificationRequest(request: VerificationRequest) {
        ValidationUtils.validateCertificateNumber(request.certificateNumber)
        ValidationUtils.validateRequired(request.verifierOrganization, "Verifier Organization")
        ValidationUtils.validateEmail(request.verifierEmail)
        ValidationUtils.validateRequired(request.paymentMethod, "Payment Method")

        if (request.paymentAmount <= 0) {
            throw BusinessException(
                "Payment amount must be positive",
                ErrorCode.VALIDATION_ERROR
            )
        }

        val validPaymentMethods = setOf("CREDIT_CARD", "BANK_TRANSFER", "CRYPTO")
        if (request.paymentMethod !in validPaymentMethods) {
            throw BusinessException(
                "Invalid payment method. Supported methods: ${validPaymentMethods.joinToString()}",
                ErrorCode.VALIDATION_ERROR
            )
        }
    }

    private fun mapVerificationStatus(status: String): VerificationStatus {
        return when (status.uppercase()) {
            "VERIFIED", "VALID", "SUCCESS" -> VerificationStatus.VERIFIED
            "FAILED", "INVALID", "ERROR" -> VerificationStatus.FAILED
            "PENDING", "IN_PROGRESS" -> VerificationStatus.PENDING
            "EXPIRED" -> VerificationStatus.EXPIRED
            else -> VerificationStatus.FAILED
        }
    }
}