package org.degreechain.employer.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.models.VerificationStatus
import org.degreechain.common.utils.ValidationUtils
import org.degreechain.employer.models.VerificationRequest
import org.degreechain.employer.models.VerificationResult
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

private val logger = KotlinLogging.logger {}

@Service
class VerificationService(
    private val contractInvoker: ContractInvoker,
    private val paymentService: PaymentService
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

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
                certificateNumber = request.certificateNumber
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
                extractionMethod = verificationData["extractionMethod"] as? String,
                additionalInfo = extractAdditionalInfo(verificationData)
            )

            logger.info { "Degree verification completed successfully: ${result.verificationId}" }
            result

        } catch (e: BusinessException) {
            logger.error(e) { "Degree verification failed: ${request.certificateNumber}" }
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during degree verification: ${request.certificateNumber}" }
            throw BusinessException(
                "Verification failed due to technical error: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun batchVerifyDegrees(requests: List<VerificationRequest>): List<VerificationResult> = withContext(Dispatchers.IO) {
        logger.info { "Starting batch verification for ${requests.size} certificates" }

        val results = mutableListOf<VerificationResult>()
        var successCount = 0
        var failureCount = 0

        requests.forEach { request ->
            try {
                val result = verifyDegree(request)
                results.add(result)

                if (result.verificationStatus == VerificationStatus.VERIFIED) {
                    successCount++
                } else {
                    failureCount++
                }
            } catch (e: Exception) {
                logger.warn(e) { "Failed to verify certificate in batch: ${request.certificateNumber}" }

                // Create failed result
                val failedResult = VerificationResult(
                    verificationId = UUID.randomUUID().toString(),
                    certificateNumber = request.certificateNumber,
                    verificationStatus = VerificationStatus.FAILED,
                    confidence = 0.0,
                    verifierOrganization = request.verifierOrganization,
                    verifierEmail = request.verifierEmail,
                    verificationTimestamp = LocalDateTime.now(),
                    paymentAmount = request.paymentAmount,
                    additionalInfo = mapOf("error" to (e.message ?: "Unknown error"))
                )

                results.add(failedResult)
                failureCount++
            }
        }

        logger.info { "Batch verification completed: $successCount successful, $failureCount failed" }
        results
    }

    suspend fun getVerificationHistory(
        organizationName: String,
        page: Int,
        size: Int,
        status: VerificationStatus?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving verification history for organization: $organizationName" }

        // In a real implementation, this would query verification history from blockchain or database
        // For now, return mock data structure
        val mockHistory = generateMockVerificationHistory(organizationName, status)

        val startIndex = page * size
        val endIndex = minOf(startIndex + size, mockHistory.size)
        val paginatedHistory = if (startIndex < mockHistory.size) {
            mockHistory.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        safeMapOf(
            "verifications" to paginatedHistory,
            "page" to page,
            "size" to size,
            "totalElements" to mockHistory.size,
            "totalPages" to (mockHistory.size + size - 1) / size,
            "hasNext" to (endIndex < mockHistory.size),
            "hasPrevious" to (page > 0),
            "filterStatus" to status?.name
        )
    }

    suspend fun getVerificationStatistics(organizationName: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving verification statistics for organization: $organizationName" }

        // In a real implementation, this would aggregate verification data
        val mockHistory = generateMockVerificationHistory(organizationName, null)

        val totalVerifications = mockHistory.size
        val successfulVerifications = mockHistory.count {
            (it["verificationStatus"] as String) == "VERIFIED"
        }
        val failedVerifications = totalVerifications - successfulVerifications
        val successRate = if (totalVerifications > 0) {
            successfulVerifications.toDouble() / totalVerifications
        } else {
            0.0
        }

        val totalSpent = mockHistory.sumOf {
            (it["paymentAmount"] as? Number)?.toDouble() ?: 0.0
        }

        safeMapOf(
            "organizationName" to organizationName,
            "totalVerifications" to totalVerifications,
            "successfulVerifications" to successfulVerifications,
            "failedVerifications" to failedVerifications,
            "successRate" to successRate,
            "totalAmountSpent" to totalSpent,
            "averageCostPerVerification" to if (totalVerifications > 0) totalSpent / totalVerifications else 0.0,
            "lastVerificationDate" to mockHistory.maxByOrNull {
                it["verificationTimestamp"] as String
            }?.get("verificationTimestamp"),
            "mostVerifiedUniversity" to findMostVerifiedUniversity(mockHistory)
        )
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
                "Invalid payment method. Must be one of: ${validPaymentMethods.joinToString()}",
                ErrorCode.VALIDATION_ERROR
            )
        }
    }

    private fun mapVerificationStatus(blockchainStatus: String): VerificationStatus {
        return when (blockchainStatus.uppercase()) {
            "VERIFIED" -> VerificationStatus.VERIFIED
            "FAILED" -> VerificationStatus.FAILED
            "EXPIRED" -> VerificationStatus.EXPIRED
            "REVOKED" -> VerificationStatus.REVOKED
            else -> VerificationStatus.FAILED
        }
    }

    private fun extractAdditionalInfo(verificationData: Map<String, Any>): Map<String, Any> {
        return safeMapOf(
            "verificationTimestamp" to verificationData["verificationTimestamp"],
            "extractionMethod" to verificationData["extractionMethod"],
            "blockchainTransactionId" to UUID.randomUUID().toString() // Mock transaction ID
        )
    }

    private fun generateMockVerificationHistory(
        organizationName: String,
        statusFilter: VerificationStatus?
    ): List<Map<String, Any>> {
        val mockData = listOf(
            safeMapOf(
                "verificationId" to "ver_001",
                "certificateNumber" to "BSc-12700",
                "verificationStatus" to "VERIFIED",
                "studentName" to "John Doe",
                "degreeName" to "Bachelor of Science in Computer Science",
                "universityName" to "University of Technology",
                "verificationTimestamp" to LocalDateTime.now().minusDays(1).format(dateFormatter),
                "paymentAmount" to 10.0
            ),
            safeMapOf(
                "verificationId" to "ver_002",
                "certificateNumber" to "MSc-45678",
                "verificationStatus" to "VERIFIED",
                "studentName" to "Jane Smith",
                "degreeName" to "Master of Science in Data Science",
                "universityName" to "Tech University",
                "verificationTimestamp" to LocalDateTime.now().minusDays(3).format(dateFormatter),
                "paymentAmount" to 15.0
            ),
            safeMapOf(
                "verificationId" to "ver_003",
                "certificateNumber" to "BSc-99999",
                "verificationStatus" to "FAILED",
                "verificationTimestamp" to LocalDateTime.now().minusDays(5).format(dateFormatter),
                "paymentAmount" to 10.0
            )
        )

        return if (statusFilter != null) {
            mockData.filter { (it["verificationStatus"] as String) == statusFilter.name }
        } else {
            mockData
        }
    }

    private fun findMostVerifiedUniversity(history: List<Map<String, Any>>): String? {
        return history
            .mapNotNull { it["universityName"] as? String }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
    }

    private fun generateCsvReport(
        history: List<Map<String, Any>>,
        startDate: LocalDateTime?,
        endDate: LocalDateTime?
    ): String {
        val header = "Verification ID,Certificate Number,Status,Student Name,Degree Name,University,Timestamp,Amount"
        val rows = history.map { record ->
            "${record["verificationId"]},${record["certificateNumber"]},${record["verificationStatus"]}," +
                    "${record["studentName"] ?: ""},${record["degreeName"] ?: ""},${record["universityName"] ?: ""}," +
                    "${record["verificationTimestamp"]},${record["paymentAmount"]}"
        }

        return (listOf(header) + rows).joinToString("\n")
    }
}