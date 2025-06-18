package org.degreechain.authority.models

import org.degreechain.common.models.BaseEntity
import javax.validation.constraints.*

data class UniversityRegistration(
    @field:NotBlank(message = "University code is required")
    @field:Pattern(
        regexp = "^[A-Z]{2,10}$",
        message = "University code must be 2-10 uppercase letters"
    )
    val universityCode: String,

    @field:NotBlank(message = "University name is required")
    @field:Size(min = 3, max = 200, message = "University name must be between 3 and 200 characters")
    val universityName: String,

    @field:NotBlank(message = "Country is required")
    @field:Size(min = 2, max = 100, message = "Country must be between 2 and 100 characters")
    val country: String,

    @field:NotBlank(message = "Address is required")
    @field:Size(max = 500, message = "Address must not exceed 500 characters")
    val address: String,

    @field:NotBlank(message = "Contact email is required")
    @field:Email(message = "Invalid email format")
    val contactEmail: String,

    @field:NotBlank(message = "Public key is required")
    val publicKey: String,

    @field:DecimalMin(value = "1000.0", message = "Minimum stake amount is 1000")
    @field:DecimalMax(value = "1000000.0", message = "Maximum stake amount is 1,000,000")
    val stakeAmount: Double,

    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "^(CRYPTO|BANK_TRANSFER|CREDIT_CARD)$",
        message = "Payment method must be CRYPTO, BANK_TRANSFER, or CREDIT_CARD"
    )
    val paymentMethod: String,

    val accreditation: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
) : BaseEntity()
