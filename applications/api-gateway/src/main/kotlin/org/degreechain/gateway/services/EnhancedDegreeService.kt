package org.degreechain.gateway.services

import org.degreechain.gateway.models.*
import org.degreechain.gateway.config.IntegrationConfig
import org.degreechain.blockchain.FabricGatewayClient  // Fixed import
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue  // Fixed import for ObjectMapper readValue
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Enhanced degree service that integrates VeryPhy API with blockchain
 */
@Service
class EnhancedDegreeService(
    private val veryphyClient: VeryPhyApiClient,
    private val blockchainClient: FabricGatewayClient,  // Fixed parameter name
    private val config: IntegrationConfig,
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(EnhancedDegreeService::class.java)
    private val processingJobs = ConcurrentHashMap<String, ProcessingStatus>()
    private val semaphore = Semaphore(config.maxConcurrentProcessing)

    /**
     * Submit a degree with VeryPhy processing and blockchain storage
     */
    suspend fun submitDegreeWithProcessing(
        certificateFile: MultipartFile,
        request: EnhancedDegreeSubmissionRequest
    ): EnhancedDegreeSubmissionResult = withContext(Dispatchers.IO) {
        val operationId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logger.info("Starting degree submission with processing. Operation ID: $operationId")

        try {
            // Update processing status
            updateProcessingStatus(operationId, ProcessingState.IN_PROGRESS, "Processing certificate with VeryPhy API")

            // Process certificate with VeryPhy API
            val processedCertificate = veryphyClient.processCertificate(
                file = certificateFile,
                embedHash = true,
                addWatermark = request.enableWatermark,
                watermarkText = request.watermarkText ?: "VERIFIED"
            )

            updateProcessingStatus(operationId, ProcessingState.IN_PROGRESS, "Submitting to blockchain")

            // Submit to blockchain
            val blockchainResult = blockchainClient.submitTransaction(
                "submitDegreeWithHash",
                request.studentId,
                request.degreeName,
                request.institutionName,
                request.issuanceDate,
                processedCertificate.hash,
                processedCertificate.ocrData ?: "{}",
                processedCertificate.processedImageUrl ?: ""
            )

            val processingTime = System.currentTimeMillis() - startTime
            updateProcessingStatus(operationId, ProcessingState.COMPLETED, "Degree submitted successfully")

            EnhancedDegreeSubmissionResult(
                success = blockchainResult.success,
                operationId = operationId,
                degreeId = blockchainResult.result,
                certificateHash = processedCertificate.hash,
                processedImageUrl = processedCertificate.processedImageUrl,
                ocrData = processedCertificate.ocrData,
                blockchainTransactionId = blockchainResult.transactionId,
                processingTime = processingTime,
                message = if (blockchainResult.success) "Degree submitted successfully" else "Submission failed",
                verifyPhyProcessingId = processedCertificate.processingId
            )

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Error in degree submission", e)
            updateProcessingStatus(operationId, ProcessingState.FAILED, "Submission failed: ${e.message}")

            EnhancedDegreeSubmissionResult(
                success = false,
                operationId = operationId,
                degreeId = null,
                certificateHash = null,
                processedImageUrl = null,
                ocrData = null,
                blockchainTransactionId = null,
                processingTime = processingTime,
                message = "Submission failed: ${e.message}",
                verifyPhyProcessingId = null
            )
        }
    }

    /**
     * Dual verification using both hash and OCR comparison
     */
    suspend fun dualVerifyDegree(
        certificateFile: MultipartFile,
        expectedHash: String?,
        verifierOrganization: String
    ): EnhancedVerificationResult = withContext(Dispatchers.IO) {
        val verificationId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        try {
            logger.info("Starting dual verification. Verification ID: $verificationId")

            // Step 1: Extract hash and OCR data from certificate
            val extractionResult = veryphyClient.extractCertificateData(certificateFile)
            val extractedHash = extractionResult.extractedHash
            val ocrData = extractionResult.certificateData

            // Step 2: Verify on blockchain using extracted hash
            val blockchainResult = if (extractedHash != null) {
                blockchainClient.evaluateTransaction("verifyDegreeByHash", extractedHash, ocrData?.toString() ?: "{}")
            } else {
                null
            }

            // Step 3: Calculate confidence scores
            val hashMatchScore = if (expectedHash != null && extractedHash != null) {
                if (expectedHash == extractedHash) 1.0 else 0.0
            } else null

            val ocrMatchScore = if (blockchainResult != null) {
                try {
                    val result: Map<String, Any> = objectMapper.readValue(blockchainResult.data ?: "{}")
                    (result["confidence"] as? Number)?.toDouble() ?: 0.0
                } catch (e: Exception) {
                    logger.warn("Failed to parse blockchain result for OCR match score", e)
                    0.0
                }
            } else null

            // Step 4: Calculate overall confidence
            val confidence = calculateOverallConfidence(hashMatchScore, ocrMatchScore)
            val verified = confidence >= 0.8
            val verificationMethod = determineVerificationMethod(hashMatchScore, ocrMatchScore)

            val processingTime = System.currentTimeMillis() - startTime

            logger.info("Dual verification completed. Verified: $verified, Method: $verificationMethod, Confidence: $confidence")

            EnhancedVerificationResult(
                verified = verified,
                confidence = confidence,
                verificationMethod = verificationMethod,
                degreeId = blockchainResult?.data?.let {
                    try {
                        val result: Map<String, Any> = objectMapper.readValue(it)
                        result["degreeId"] as? String
                    } catch (e: Exception) { null }
                },
                certificateData = ocrData,
                blockchainRecord = blockchainResult?.data,
                extractedHash = extractedHash,
                expectedHash = expectedHash,
                ocrMatchScore = ocrMatchScore,
                hashMatchScore = hashMatchScore,
                message = if (verified) "Verification successful" else "Verification failed",
                processingTime = processingTime,
                verificationId = verificationId
            )

        } catch (e: Exception) {
            logger.error("Error in dual verification", e)
            val processingTime = System.currentTimeMillis() - startTime

            EnhancedVerificationResult(
                verified = false,
                confidence = 0.0,
                verificationMethod = "ERROR",
                degreeId = null,
                certificateData = null,
                blockchainRecord = null,
                extractedHash = null,
                expectedHash = expectedHash,
                ocrMatchScore = null,
                hashMatchScore = null,
                message = "Verification failed: ${e.message}",
                processingTime = processingTime,
                verificationId = verificationId
            )
        }
    }

    /**
     * Batch verify multiple certificates
     */
    suspend fun batchVerifyCertificates(
        files: List<MultipartFile>,
        expectedHashes: List<String>?,
        verifierOrganization: String
    ): BatchVerificationResult = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logger.info("Starting batch verification. Batch ID: $batchId, Files: ${files.size}")

        try {
            // Process files in parallel with semaphore to limit concurrency
            val results = files.mapIndexed { index, file ->
                async {
                    semaphore.withPermit {
                        val expectedHash = expectedHashes?.getOrNull(index)
                        try {
                            dualVerifyDegree(file, expectedHash, verifierOrganization)
                        } catch (e: Exception) {
                            logger.error("Error verifying file ${index + 1}", e)
                            EnhancedVerificationResult(
                                verified = false,
                                confidence = 0.0,
                                verificationMethod = "ERROR",
                                degreeId = null,
                                certificateData = null,
                                blockchainRecord = null,
                                extractedHash = null,
                                expectedHash = expectedHash,
                                ocrMatchScore = null,
                                hashMatchScore = null,
                                message = "Processing failed: ${e.message}",
                                processingTime = 0L,
                                verificationId = UUID.randomUUID().toString()
                            )
                        }
                    }
                }
            }.awaitAll()  // Fixed: awaitAll() on List<Deferred>

            val successCount = results.count { it.verified }
            val failureCount = results.size - successCount
            val processingTime = System.currentTimeMillis() - startTime

            logger.info("Batch verification completed. Success: $successCount, Failed: $failureCount")

            BatchVerificationResult(
                batchId = batchId,
                results = results,
                totalProcessed = results.size,
                successCount = successCount,
                failureCount = failureCount,
                processingTime = processingTime
            )

        } catch (e: Exception) {
            logger.error("Error in batch verification", e)
            BatchVerificationResult(
                batchId = batchId,
                results = emptyList(),
                totalProcessed = files.size,
                successCount = 0,
                failureCount = files.size,
                processingTime = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Batch submit multiple degrees
     */
    suspend fun batchSubmitDegrees(
        files: List<MultipartFile>,
        requests: List<EnhancedDegreeSubmissionRequest>
    ): BatchSubmissionResult = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logger.info("Starting batch degree submission. Batch ID: $batchId, Files: ${files.size}")

        try {
            val results = files.mapIndexed { index, file ->
                async {
                    semaphore.withPermit {
                        val request = requests.getOrNull(index)
                        if (request != null) {
                            try {
                                submitDegreeWithProcessing(file, request)
                            } catch (e: Exception) {
                                logger.error("Error submitting degree ${index + 1}", e)
                                EnhancedDegreeSubmissionResult(
                                    success = false,
                                    operationId = UUID.randomUUID().toString(),
                                    degreeId = null,
                                    certificateHash = null,
                                    processedImageUrl = null,
                                    ocrData = null,
                                    blockchainTransactionId = null,
                                    processingTime = 0L,
                                    message = "Submission failed: ${e.message}",
                                    verifyPhyProcessingId = null
                                )
                            }
                        } else {
                            logger.error("No request found for file ${index + 1}")
                            EnhancedDegreeSubmissionResult(
                                success = false,
                                operationId = UUID.randomUUID().toString(),
                                degreeId = null,
                                certificateHash = null,
                                processedImageUrl = null,
                                ocrData = null,
                                blockchainTransactionId = null,
                                processingTime = 0L,
                                message = "No request data provided",
                                verifyPhyProcessingId = null
                            )
                        }
                    }
                }
            }.awaitAll()

            val successCount = results.count { it.success }
            val failureCount = results.size - successCount
            val processingTime = System.currentTimeMillis() - startTime

            logger.info("Batch submission completed. Success: $successCount, Failed: $failureCount")

            BatchSubmissionResult(
                batchId = batchId,
                results = results,
                totalProcessed = results.size,
                successCount = successCount,
                failureCount = failureCount,
                processingTime = processingTime
            )

        } catch (e: Exception) {
            logger.error("Error in batch submission", e)
            BatchSubmissionResult(
                batchId = batchId,
                results = emptyList(),
                totalProcessed = files.size,
                successCount = 0,
                failureCount = files.size,
                processingTime = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Get processing status for long-running operations
     */
    fun getProcessingStatus(operationId: String): ProcessingStatus? {
        return processingJobs[operationId]
    }

    /**
     * Get system analytics
     */
    suspend fun getSystemAnalytics(): SystemAnalytics = withContext(Dispatchers.IO) {
        // This would typically query the blockchain for real analytics
        // For now, return mock data
        SystemAnalytics(
            totalDegrees = 1250L,
            totalVerifications = 3450L,
            totalUniversities = 45L,
            averageVerificationTime = 2.3,
            successRate = 98.7,
            topUniversities = listOf(
                UniversityStats("MIT", 150L, 450L, 99.2, 0.96),
                UniversityStats("Stanford", 130L, 380L, 98.8, 0.95),
                UniversityStats("Harvard", 120L, 350L, 99.1, 0.97)
            ),
            verificationTrends = listOf(
                VerificationTrend("2024-06-01", 125L, 0.95, 98.4),
                VerificationTrend("2024-06-02", 142L, 0.96, 98.9),
                VerificationTrend("2024-06-03", 138L, 0.94, 97.8)
            ),
            confidenceDistribution = mapOf(
                "High (>90%)" to 78,
                "Medium (70-90%)" to 18,
                "Low (<70%)" to 4
            )
        )
    }

    // ========== PRIVATE HELPER METHODS ==========

    private fun updateProcessingStatus(operationId: String, state: ProcessingState, message: String) {
        processingJobs[operationId] = ProcessingStatus(
            operationId = operationId,
            state = state,
            message = message,
            progress = when (state) {
                ProcessingState.PENDING -> 0
                ProcessingState.IN_PROGRESS -> 50
                ProcessingState.COMPLETED -> 100
                ProcessingState.FAILED -> 0
                ProcessingState.CANCELLED -> 0
            },
            startTime = System.currentTimeMillis(),
            lastUpdated = System.currentTimeMillis()
        )
    }

    private fun calculateOverallConfidence(hashMatchScore: Double?, ocrMatchScore: Double?): Double {
        return when {
            hashMatchScore != null && ocrMatchScore != null -> (hashMatchScore + ocrMatchScore) / 2.0
            hashMatchScore != null -> hashMatchScore
            ocrMatchScore != null -> ocrMatchScore
            else -> 0.0
        }
    }

    private fun determineVerificationMethod(hashMatchScore: Double?, ocrMatchScore: Double?): String {
        return when {
            hashMatchScore != null && ocrMatchScore != null -> "HASH_AND_OCR_MATCH"
            hashMatchScore != null -> "HASH_MATCH"
            ocrMatchScore != null -> "OCR_MATCH"
            else -> "NO_MATCH"
        }
    }
}