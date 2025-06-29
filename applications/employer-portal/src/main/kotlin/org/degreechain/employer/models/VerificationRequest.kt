package org.degreechain.employer.models

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern

data class VerificationRequest(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:NotBlank(message = "Verifier organization is required")
    val verifierOrganization: String,

    @field:NotBlank(message = "Verifier email is required")
    @field:Email(message = "Invalid email format")
    val verifierEmail: String,

    @field:Min(value = 1, message = "Payment amount must be positive")
    val paymentAmount: Double,

    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "^(CREDIT_CARD|BANK_TRANSFER|CRYPTO)$",
        message = "Invalid payment method"
    )
    val paymentMethod: String,

    val providedHash: String? = null,
    val additionalNotes: String? = null
)
