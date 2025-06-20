package org.degreechain.employer.models

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime
import jakarta.validation.constraints.*

data class VerificationRequest(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:NotBlank(message = "Verifier organization is required")
    val verifierOrganization: String,

    @field:Email(message = "Invalid email format")
    @field:NotBlank(message = "Verifier email is required")
    val verifierEmail: String,

    @field:NotBlank(message = "Payment method is required")
    val paymentMethod: String,

    @field:Min(value = 1, message = "Payment amount must be positive")
    val paymentAmount: Double,

    val providedHash: String? = null,
    val additionalNotes: String? = null,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val requestTimestamp: LocalDateTime = LocalDateTime.now()
)