package org.degreechain.models

import java.time.LocalDateTime


/**
 * Verification result with confidence scoring
 */
/*
data class VerificationResultWithConfidence(
    val verified: Boolean,
    val degreeId: String?,
    val degree: DegreeWithHash? = null,
    val verificationMethod: String,
    val confidence: Double,
    val message: String,
    val timestamp: LocalDateTime
)
*/

/**
 * Verification log entry for audit trail
 */
/*data class VerificationLogEntry(
    val verificationId: String,
    val degreeId: String,
    val verifierOrg: String,
    val verificationMethod: String,
    val confidence: Double,
    val timestamp: LocalDateTime,
    val extractedHash: String
)*/

/**
 * Enhanced university model with submission tracking
 */
/*data class University(
    val universityCode: String,
    val universityName: String,
    val country: String,
    val contactEmail: String,
    val publicKey: String,
    val registrationDate: LocalDateTime,
    val isActive: Boolean,
    val stake: Double,
    val totalEarnings: Double,
    val verificationCount: Int,
    val submissionCount: Int = 0
)*/



/**
 * Verification request model
 */
data class VerificationRequest(
    val certificateHash: String,
    val ocrData: String? = null,
    val verifierOrganization: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Payment transaction model
 */
data class PaymentTransaction(
    val transactionId: String,
    val verificationId: String,
    val payerOrganization: String,
    val amount: Double,
    val currency: String = "USD",
    val timestamp: LocalDateTime,
    val status: PaymentStatus
)

/**
 * Payment status enumeration
 */
enum class PaymentStatus {
    PENDING,
    COMPLETED,
    FAILED,
    REFUNDED
}

/**
 * Audit log entry
 */
data class AuditLogEntry(
    val logId: String,
    val action: String,
    val performedBy: String,
    val targetId: String,
    val details: Map<String, Any>,
    val timestamp: LocalDateTime
)

/**
 * System statistics model
 */
data class SystemStatistics(
    val totalUniversities: Int,
    val activeUniversities: Int,
    val totalDegrees: Int,
    val totalVerifications: Int,
    val totalRevocations: Int,
    val averageConfidenceScore: Double,
    val lastUpdated: LocalDateTime
)

/**
 * Batch verification request
 */
data class BatchVerificationRequest(
    val verifications: List<VerificationRequest>,
    val batchId: String,
    val requestedBy: String,
    val timestamp: LocalDateTime = LocalDateTime.now()
)

/**
 * Batch verification result
 */
data class BatchVerificationResult(
    val batchId: String,
    val totalRequests: Int,
    val successfulVerifications: Int,
    val failedVerifications: Int,
    val results: List<VerificationResultWithConfidence>,
    val processingTime: Long,
    val timestamp: LocalDateTime
)