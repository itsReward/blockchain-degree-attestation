// applications/api-gateway/src/main/kotlin/org/degreechain/gateway/models/EnhancedDegreeModels.kt
package org.degreechain.gateway.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Enhanced degree submission request
 */
data class EnhancedDegreeSubmissionRequest(
    @JsonProperty("studentId")
    val studentId: String,

    @JsonProperty("degreeName")
    val degreeName: String,

    @JsonProperty("institutionName")
    val institutionName: String,

    @JsonProperty("issuanceDate")
    val issuanceDate: String,

    @JsonProperty("enableWatermark")
    val enableWatermark: Boolean = false,

    @JsonProperty("watermarkText")
    val watermarkText: String? = "VERIFIED",

    @JsonProperty("additionalData")
    val additionalData: Map<String, Any>? = null
)

/**
 * Enhanced degree submission result
 */
data class EnhancedDegreeSubmissionResult(
    @JsonProperty("success")
    val success: Boolean,

    @JsonProperty("operationId")
    val operationId: String,

    @JsonProperty("degreeId")
    val degreeId: String?,

    @JsonProperty("certificateHash")
    val certificateHash: String?,

    @JsonProperty("processedImageUrl")
    val processedImageUrl: String?,

    @JsonProperty("ocrData")
    val ocrData: String?,

    @JsonProperty("blockchainTransactionId")
    val blockchainTransactionId: String?,

    @JsonProperty("processingTime")
    val processingTime: Long,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("verifyPhyProcessingId")
    val verifyPhyProcessingId: String?
)

/**
 * Enhanced verification result
 */
data class EnhancedVerificationResult(
    @JsonProperty("verified")
    val verified: Boolean,

    @JsonProperty("confidence")
    val confidence: Double,

    @JsonProperty("verificationMethod")
    val verificationMethod: String,

    @JsonProperty("degreeId")
    val degreeId: String?,

    @JsonProperty("certificateData")
    val certificateData: Any?,

    @JsonProperty("blockchainRecord")
    val blockchainRecord: String?,

    @JsonProperty("extractedHash")
    val extractedHash: String?,

    @JsonProperty("expectedHash")
    val expectedHash: String?,

    @JsonProperty("ocrMatchScore")
    val ocrMatchScore: Double?,

    @JsonProperty("hashMatchScore")
    val hashMatchScore: Double?,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("processingTime")
    val processingTime: Long,

    @JsonProperty("verificationId")
    val verificationId: String
)

/**
 * Batch verification result
 */
data class BatchVerificationResult(
    @JsonProperty("batchId")
    val batchId: String,

    @JsonProperty("results")
    val results: List<EnhancedVerificationResult>,

    @JsonProperty("totalProcessed")
    val totalProcessed: Int,

    @JsonProperty("successCount")
    val successCount: Int,

    @JsonProperty("failureCount")
    val failureCount: Int,

    @JsonProperty("processingTime")
    val processingTime: Long
)

/**
 * Batch submission result
 */
data class BatchSubmissionResult(
    @JsonProperty("batchId")
    val batchId: String,

    @JsonProperty("results")
    val results: List<EnhancedDegreeSubmissionResult>,

    @JsonProperty("totalProcessed")
    val totalProcessed: Int,

    @JsonProperty("successCount")
    val successCount: Int,

    @JsonProperty("failureCount")
    val failureCount: Int,

    @JsonProperty("processingTime")
    val processingTime: Long
)

/**
 * Processing status
 */
data class ProcessingStatus(
    @JsonProperty("operationId")
    val operationId: String,

    @JsonProperty("state")
    val state: ProcessingState,

    @JsonProperty("message")
    val message: String,

    @JsonProperty("progress")
    val progress: Int,

    @JsonProperty("startTime")
    val startTime: Long,

    @JsonProperty("lastUpdated")
    val lastUpdated: Long
)

/**
 * Processing state enumeration
 */
enum class ProcessingState {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Verification extraction response
 */
data class VerificationExtractionResponse(
    @JsonProperty("extractedHash")
    val extractedHash: String?,

    @JsonProperty("certificateData")
    val certificateData: Any?,

    @JsonProperty("processingTime")
    val processingTime: Long,

    @JsonProperty("confidence")
    val confidence: Double,

    @JsonProperty("extractionMethod")
    val extractionMethod: String
)