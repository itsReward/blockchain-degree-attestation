package org.degreechain.common.utils

import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import java.util.regex.Pattern

object ValidationUtils {

    private val EMAIL_PATTERN = Pattern.compile(
        "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
    )

    private val CERTIFICATE_NUMBER_PATTERN = Pattern.compile(
        "^[A-Za-z]+-\\d+$"
    )

    private val UNIVERSITY_CODE_PATTERN = Pattern.compile(
        "^[A-Z]{2,10}$"
    )

    fun validateEmail(email: String?): String {
        if (email.isNullOrBlank()) {
            throw BusinessException("Email is required", ErrorCode.VALIDATION_ERROR)
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw BusinessException("Invalid email format", ErrorCode.VALIDATION_ERROR)
        }
        return email.lowercase()
    }

    fun validateCertificateNumber(certificateNumber: String?): String {
        if (certificateNumber.isNullOrBlank()) {
            throw BusinessException("Certificate number is required", ErrorCode.VALIDATION_ERROR)
        }
        if (!CERTIFICATE_NUMBER_PATTERN.matcher(certificateNumber).matches()) {
            throw BusinessException(
                "Invalid certificate number format. Expected format: ABC-12345",
                ErrorCode.VALIDATION_ERROR
            )
        }
        return certificateNumber.uppercase()
    }

    fun validateUniversityCode(universityCode: String?): String {
        if (universityCode.isNullOrBlank()) {
            throw BusinessException("University code is required", ErrorCode.VALIDATION_ERROR)
        }
        if (!UNIVERSITY_CODE_PATTERN.matcher(universityCode).matches()) {
            throw BusinessException(
                "Invalid university code format. Expected 2-10 uppercase letters",
                ErrorCode.VALIDATION_ERROR
            )
        }
        return universityCode.uppercase()
    }

    fun validateHash(hash: String?): String {
        if (hash.isNullOrBlank()) {
            throw BusinessException("Hash is required", ErrorCode.VALIDATION_ERROR)
        }
        if (hash.length != 64) {
            throw BusinessException("Hash must be 64 characters long", ErrorCode.VALIDATION_ERROR)
        }
        if (!hash.matches(Regex("^[a-fA-F0-9]+$"))) {
            throw BusinessException("Hash must be hexadecimal", ErrorCode.VALIDATION_ERROR)
        }
        return hash.lowercase()
    }

    fun validateStakeAmount(amount: Double): Double {
        if (amount <= 0) {
            throw BusinessException("Stake amount must be positive", ErrorCode.VALIDATION_ERROR)
        }
        if (amount < 1000.0) { // Minimum stake requirement
            throw BusinessException("Minimum stake amount is 1000", ErrorCode.INSUFFICIENT_STAKE)
        }
        return amount
    }

    fun validateRequired(value: String?, fieldName: String): String {
        if (value.isNullOrBlank()) {
            throw BusinessException("$fieldName is required", ErrorCode.VALIDATION_ERROR)
        }
        return value.trim()
    }

    fun validateStringLength(value: String?, fieldName: String, minLength: Int, maxLength: Int): String {
        val validated = validateRequired(value, fieldName)
        if (validated.length < minLength || validated.length > maxLength) {
            throw BusinessException(
                "$fieldName must be between $minLength and $maxLength characters",
                ErrorCode.VALIDATION_ERROR
            )
        }
        return validated
    }

    fun validatePhoneNumber(phoneNumber: String?) {
        if (!phoneNumber.isNullOrBlank()) {
            val phoneRegex = "^[+]?[1-9]\\d{1,14}$".toRegex()
            if (!phoneNumber.matches(phoneRegex)) {
                throw BusinessException("Invalid phone number format", ErrorCode.VALIDATION_ERROR)
            }
        }
    }

    fun validatePositiveAmount(amount: Double?, fieldName: String) {
        if (amount == null || amount <= 0) {
            throw BusinessException("$fieldName must be a positive number", ErrorCode.VALIDATION_ERROR)
        }
    }

    fun validateDateRange(startDate: String?, endDate: String?) {
        if (!startDate.isNullOrBlank() && !endDate.isNullOrBlank()) {
            try {
                val start = java.time.LocalDateTime.parse(startDate)
                val end = java.time.LocalDateTime.parse(endDate)
                if (start.isAfter(end)) {
                    throw BusinessException("Start date must be before end date", ErrorCode.VALIDATION_ERROR)
                }
            } catch (e: java.time.format.DateTimeParseException) {
                throw BusinessException("Invalid date format. Use ISO format (yyyy-MM-ddTHH:mm:ss)", ErrorCode.VALIDATION_ERROR)
            }
        }
    }

    fun validatePageParams(page: Int, size: Int) {
        if (page < 0) {
            throw BusinessException("Page number cannot be negative", ErrorCode.VALIDATION_ERROR)
        }
        if (size < 1 || size > 100) {
            throw BusinessException("Page size must be between 1 and 100", ErrorCode.VALIDATION_ERROR)
        }
    }

    fun validateEnumValue(value: String?, validValues: Set<String>, fieldName: String) {
        if (!value.isNullOrBlank() && value !in validValues) {
            throw BusinessException(
                "$fieldName must be one of: ${validValues.joinToString(", ")}",
                ErrorCode.VALIDATION_ERROR
            )
        }
    }
}