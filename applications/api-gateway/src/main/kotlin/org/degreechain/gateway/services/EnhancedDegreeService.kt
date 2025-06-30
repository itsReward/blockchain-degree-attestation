package org.degreechain.gateway.services

import org.degreechain.gateway.models.*
import org.degreechain.gateway.config.IntegrationConfig
import org.degreechain.blockchain.FabricGatewayClient
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Enhanced degree service that integrates VeryPhy API with blockchain
 */
@Service
class EnhancedDegreeService(
    private val veryphyClient: VeryPhyApiClient,
    private val blockchainClient: FabricGatewayClient,
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

            // Submit to blockchain - Fixed parameter names and extraction
            val blockchainResult = blockchainClient.submitTransaction(
                functionName = "submitDegreeWithHash",
                args = arrayOf(
                    request.studentId,
                    request.degreeName,
                    request.institutionName,
                    request.issuanceDate,
                    processedCertificate.hash,
                    objectMapper.writeValueAsString(processedCertificate.certificateData ?: mapOf<String, Any>()),
                    processedCertificate.processedImageUrl ?: ""
                )
            )

            val processingTime = System.currentTimeMillis() - startTime
            updateProcessingStatus(operationId, ProcessingState.COMPLETED, "Degree submitted successfully")

            EnhancedDegreeSubmissionResult(
                success = blockchainResult.success,
                operationId = operationId,
                degreeId = blockchainResult.result,
                certificateHash = processedCertificate.hash,
                processedImageUrl = processedCertificate.processedImageUrl,
                ocrData = objectMapper.writeValueAsString(processedCertificate.certificateData ?: mapOf<String, Any>()),
                blockchainTransactionId = blockchainResult.transactionId,
                processingTime = processingTime,
                message = if (blockchainResult.success) "Degree submitted successfully" else "Submission failed",
                verifyPhyProcessingId = processedCertificate.certificateNumber
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
            val extractedHash = extractionResult.hash
            val certificateData = extractionResult.certificateData

            // Step 2: Verify on blockchain using extracted hash
            val blockchainResult = if (extractedHash != null) {
                blockchainClient.evaluateTransaction(
                    functionName = "verifyDegreeByHash",
                    args = arrayOf(
                        extractedHash,
                        objectMapper.writeValueAsString(certificateData ?: mapOf<String, Any>())
                    )
                )
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
                    } catch (e: Exception) {
                        logger.warn("Failed to extract degreeId from blockchain result", e)
                        null
                    }
                },
                certificateData = certificateData,
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
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Error in dual verification", e)

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
     * Alternative method name for compatibility
     */
    suspend fun verifyDegreeWithDualMethod(
        certificateFile: MultipartFile,
        expectedHash: String?
    ): EnhancedVerificationResult {
        return dualVerifyDegree(certificateFile, expectedHash, "DEFAULT")
    }

    /**
     * Batch submission of degrees
     */
    suspend fun submitDegreesBatch(
        files: List<MultipartFile>,
        requests: List<EnhancedDegreeSubmissionRequest>
    ): BatchSubmissionResult = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logger.info("Starting batch submission. Batch ID: $batchId, Files: ${files.size}")

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
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Error in batch submission", e)

            BatchSubmissionResult(
                batchId = batchId,
                results = emptyList(),
                totalProcessed = 0,
                successCount = 0,
                failureCount = files.size,
                processingTime = processingTime
            )
        }
    }

    /**
     * Batch verification of degrees
     */
    suspend fun verifyDegreesBatch(
        files: List<MultipartFile>,
        expectedHashes: List<String?> = emptyList()
    ): BatchVerificationResult = withContext(Dispatchers.IO) {
        val batchId = UUID.randomUUID().toString()
        val startTime = System.currentTimeMillis()

        logger.info("Starting batch verification. Batch ID: $batchId, Files: ${files.size}")

        try {
            val results = files.mapIndexed { index, file ->
                async {
                    semaphore.withPermit {
                        val expectedHash = expectedHashes.getOrNull(index)
                        try {
                            dualVerifyDegree(file, expectedHash, "BATCH_VERIFICATION")
                        } catch (e: Exception) {
                            logger.error("Error verifying degree ${index + 1}", e)
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
                                processingTime = 0L,
                                verificationId = UUID.randomUUID().toString()
                            )
                        }
                    }
                }
            }.awaitAll()

            val successCount = results.count { it.verified }
            val failureCount = results.size - successCount
            val processingTime = System.currentTimeMillis() - startTime

            logger.info("Batch verification completed. Verified: $successCount, Failed: $failureCount")

            BatchVerificationResult(
                batchId = batchId,
                results = results,
                totalProcessed = results.size,
                successCount = successCount,
                failureCount = failureCount,
                processingTime = processingTime
            )

        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            logger.error("Error in batch verification", e)

            BatchVerificationResult(
                batchId = batchId,
                results = emptyList(),
                totalProcessed = 0,
                successCount = 0,
                failureCount = files.size,
                processingTime = processingTime
            )
        }
    }

    /**
     * Get processing status for an operation
     */
    fun getProcessingStatus(operationId: String): ProcessingStatus? {
        return processingJobs[operationId]
    }

    /**
     * Update processing status
     */
    private fun updateProcessingStatus(operationId: String, state: ProcessingState, message: String) {
        val currentTime = System.currentTimeMillis()
        val existingStatus = processingJobs[operationId]

        val status = ProcessingStatus(
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
            startTime = existingStatus?.startTime ?: currentTime,
            lastUpdated = currentTime
        )

        processingJobs[operationId] = status
    }

    /**
     * Calculate overall confidence score
     */
    private fun calculateOverallConfidence(hashMatchScore: Double?, ocrMatchScore: Double?): Double {
        return when {
            hashMatchScore != null && ocrMatchScore != null -> {
                // Both scores available - weighted average (hash has higher weight)
                (hashMatchScore * 0.7) + (ocrMatchScore * 0.3)
            }
            hashMatchScore != null -> hashMatchScore
            ocrMatchScore != null -> ocrMatchScore
            else -> 0.0
        }
    }

    /**
     * Determine verification method based on available scores
     */
    private fun determineVerificationMethod(hashMatchScore: Double?, ocrMatchScore: Double?): String {
        return when {
            hashMatchScore != null && ocrMatchScore != null -> "DUAL_VERIFICATION"
            hashMatchScore != null -> "HASH_VERIFICATION"
            ocrMatchScore != null -> "OCR_VERIFICATION"
            else -> "NO_VERIFICATION"
        }
    }
}