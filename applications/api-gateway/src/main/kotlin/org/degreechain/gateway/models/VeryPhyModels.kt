package org.degreechain.gateway.models

import com.fasterxml.jackson.annotation.JsonProperty
import java.time.LocalDateTime

/**
 * Response from VeryPhy API when processing a certificate
 */
data class ProcessedCertificateResponse(
    @JsonProperty("certificate_number")
    val certificateNumber: String,
    val hash: String,
    @JsonProperty("processed_image_url")
    val processedImageUrl: String,
    @JsonProperty("certificate_data")
    val certificateData: CertificateData?,
    val confidence: Double,
    val message: String,
    @JsonProperty("processing_time")
    val processingTime: Long? = null,
    @JsonProperty("file_size")
    val fileSize: Long? = null
)

/**
 * Response from VeryPhy API when extracting certificate data
 */
data class VerificationExtractionResponse(
    @JsonProperty("verification_status")
    val verificationStatus: String,
    val confidence: Double,
    val hash: String?,
    @JsonProperty("expected_hash")
    val expectedHash: String?,
    @JsonProperty("certificate_data")
    val certificateData: CertificateData?,
    @JsonProperty("extraction_method")
    val extractionMethod: String?,
    @JsonProperty("similarity_score")
    val similarityScore: Double?,
    val message: String,
    @JsonProperty("processing_time")
    val processingTime: Long? = null
)

/**
 * Certificate data extracted via OCR
 */
data class CertificateData(
    @JsonProperty("Certificate Number")
    val certificateNumber: String?,
    @JsonProperty("Student Name")
    val studentName: String?,
    @JsonProperty("Degree Name")
    val degreeName: String?,
    @JsonProperty("Institution Name")
    val institutionName: String?,
    @JsonProperty("Issuance Date")
    val issuanceDate: String?,
    @JsonProperty("Graduation Date")
    val graduationDate: String?,
    @JsonProperty("Grade")
    val grade: String?,
    @JsonProperty("GPA")
    val gpa: String?,
    @JsonProperty("Department")
    val department: String?,
    @JsonProperty("Faculty")
    val faculty: String?
)

/**
 * Request model for enhanced degree submission
 */
data class EnhancedDegreeSubmissionRequest(
    val studentId: String,
    val degreeName: String,
    val institutionName: String,
    val issuanceDate: String,
    val transcripts: String? = null,
    val additionalData: Map<String, Any>? = null,
    val enableWatermark: Boolean = false,
    val watermarkText: String = "VERIFIED"
)

/**
 * Result of enhanced degree submission
 */
data class EnhancedDegreeSubmissionResult(
    val success: Boolean,
    val degreeId: String?,
    val transactionId: String?,
    val certificateHash: String?,
    val embeddedCertificateUrl: String?,
    val confidence: Double?,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val processingTime: Long? = null
)

/**
 * Enhanced verification result combining blockchain and VeryPhy data
 */
data class EnhancedVerificationResult(
    val verified: Boolean,
    val confidence: Double,
    val verificationMethod: String,
    val degreeId: String?,
    val certificateData: CertificateData?,
    val blockchainRecord: BlockchainDegreeRecord?,
    val extractedHash: String?,
    val expectedHash: String?,
    val ocrMatchScore: Double?,
    val hashMatchScore: Double?,
    val message: String,
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val processingTime: Long? = null,
    val verificationId: String? = null
)

/**
 * Blockchain degree record (simplified for API responses)
 */
data class BlockchainDegreeRecord(
    val degreeId: String,
    val studentId: String,
    val degreeName: String,
    val institutionName: String,
    val issuanceDate: String,
    val submissionDate: String,
    val status: String,
    val verificationCount: Int,
    val lastVerified: String?
)

/**
 * Batch verification request
 */
data class BatchVerificationRequest(
    val files: List<String>, // File identifiers or paths
    val useEnhancedExtraction: Boolean = true,
    val batchId: String? = null
)

/**
 * Batch verification result
 */
data class BatchVerificationResult(
    val batchId: String,
    val totalRequests: Int,
    val successfulVerifications: Int,
    val failedVerifications: Int,
    val results: List<EnhancedVerificationResult>,
    val processingTime: Long,
    val averageConfidence: Double,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * File upload metadata
 */
data class FileUploadMetadata(
    val filename: String,
    val fileSize: Long,
    val mimeType: String,
    val uploadedAt: LocalDateTime = LocalDateTime.now(),
    val uploadedBy: String? = null
)

/**
 * Processing status for long-running operations
 */
data class ProcessingStatus(
    val operationId: String,
    val status: ProcessingState,
    val progress: Int, // 0-100
    val message: String,
    val startedAt: LocalDateTime,
    val estimatedCompletion: LocalDateTime? = null,
    val result: Any? = null
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
 * API error response
 */
data class VeryPhyApiError(
    val error: String,
    val message: String,
    val code: String? = null,
    val details: Map<String, Any>? = null,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * System analytics data
 */
data class SystemAnalytics(
    val totalDegrees: Long,
    val totalVerifications: Long,
    val totalUniversities: Long,
    val averageVerificationTime: Double,
    val successRate: Double,
    val topUniversities: List<UniversityStats>,
    val verificationTrends: List<VerificationTrend>,
    val confidenceDistribution: Map<String, Int>,
    val generatedAt: LocalDateTime = LocalDateTime.now()
)

/**
 * University statistics
 */
data class UniversityStats(
    val universityName: String,
    val totalDegrees: Long,
    val totalVerifications: Long,
    val successRate: Double,
    val averageConfidence: Double
)

/**
 * Verification trend data
 */
data class VerificationTrend(
    val date: String,
    val verificationCount: Long,
    val averageConfidence: Double,
    val successRate: Double
)

/**
 * Certificate comparison result
 */
data class CertificateComparisonResult(
    val overallSimilarity: Double,
    val fieldMatches: Map<String, FieldComparison>,
    val discrepancies: List<String>,
    val confidence: Double,
    val recommendation: String
)

/**
 * Field comparison details
 */
data class FieldComparison(
    val fieldName: String,
    val originalValue: String?,
    val extractedValue: String?,
    val similarity: Double,
    val isMatch: Boolean
)

/**
 * Hash embedding result
 */
data class HashEmbeddingResult(
    val success: Boolean,
    val embeddedHash: String?,
    val embeddedImageUrl: String?,
    val embeddingMethod: String,
    val confidence: Double,
    val message: String
)

/**
 * Watermark configuration
 */
data class WatermarkConfig(
    val text: String = "VERIFIED",
    val opacity: Int = 40,
    val fontSize: Int = 20,
    val color: String = "#FF0000",
    val position: WatermarkPosition = WatermarkPosition.CENTER,
    val pattern: WatermarkPattern = WatermarkPattern.DIAGONAL
)

/**
 * Watermark position enumeration
 */
enum class WatermarkPosition {
    TOP_LEFT, TOP_CENTER, TOP_RIGHT,
    CENTER_LEFT, CENTER, CENTER_RIGHT,
    BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
}

/**
 * Watermark pattern enumeration
 */
enum class WatermarkPattern {
    SINGLE, DIAGONAL, GRID, BORDER
}