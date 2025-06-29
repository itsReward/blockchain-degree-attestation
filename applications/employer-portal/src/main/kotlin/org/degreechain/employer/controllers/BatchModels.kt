package org.degreechain.employer.controllers

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.degreechain.employer.models.VerificationRequest
import org.degreechain.employer.models.VerificationResult

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