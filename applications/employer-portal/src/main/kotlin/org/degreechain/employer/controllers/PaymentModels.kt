package org.degreechain.employer.controllers

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

/**
 * Request model for payment processing
 */
data class PaymentRequest(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:Min(value = 1, message = "Amount must be positive")
    val amount: Double,

    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "^(CREDIT_CARD|BANK_TRANSFER|CRYPTO)$",
        message = "Invalid payment method"
    )
    val paymentMethod: String,

    @field:NotBlank(message = "Organization name is required")
    val organizationName: String,

    val additionalInfo: Map<String, Any> = emptyMap()
)

/**
 * Request model for refund processing
 */
data class RefundRequest(
    @field:NotBlank(message = "Refund reason is required")
    val reason: String,

    @field:Min(value = 0, message = "Refund amount cannot be negative")
    val refundAmount: Double? = null // null means full refund
)

/**
 * Request model for payment cancellation
 */
data class CancelPaymentRequest(
    @field:NotBlank(message = "Cancellation reason is required")
    val reason: String
)

/**
 * Information about supported payment methods
 */
data class PaymentMethodInfo(
    val method: String,
    val displayName: String,
    val description: String,
    val minimumAmount: Double,
    val maximumAmount: Double?,
    val processingTime: String,
    val fees: Map<String, Any>,
    val enabled: Boolean
)