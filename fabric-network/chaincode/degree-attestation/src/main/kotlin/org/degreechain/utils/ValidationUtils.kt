package org.degreechain.utils

import org.hyperledger.fabric.shim.ChaincodeException
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
            throw ChaincodeException("Email is required", "VALIDATION_ERROR")
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw ChaincodeException("Invalid email format", "VALIDATION_ERROR")
        }
        return email.lowercase()
    }

    fun validateCertificateNumber(certificateNumber: String?): String {
        if (certificateNumber.isNullOrBlank()) {
            throw ChaincodeException("Certificate number is required", "VALIDATION_ERROR")
        }
        if (!CERTIFICATE_NUMBER_PATTERN.matcher(certificateNumber).matches()) {
            throw ChaincodeException(
                "Invalid certificate number format. Expected format: ABC-12345",
                "VALIDATION_ERROR"
            )
        }
        return certificateNumber.uppercase()
    }

    fun validateUniversityCode(universityCode: String?): String {
        if (universityCode.isNullOrBlank()) {
            throw ChaincodeException("University code is required", "VALIDATION_ERROR")
        }
        if (!UNIVERSITY_CODE_PATTERN.matcher(universityCode).matches()) {
            throw ChaincodeException(
                "Invalid university code format. Expected 2-10 uppercase letters",
                "VALIDATION_ERROR"
            )
        }
        return universityCode.uppercase()
    }

    fun validateHash(hash: String?): String {
        if (hash.isNullOrBlank()) {
            throw ChaincodeException("Hash is required", "VALIDATION_ERROR")
        }
        if (hash.length != 64) {
            throw ChaincodeException("Hash must be 64 characters long", "VALIDATION_ERROR")
        }
        if (!hash.matches(Regex("^[a-fA-F0-9]+$"))) {
            throw ChaincodeException("Hash must be hexadecimal", "VALIDATION_ERROR")
        }
        return hash.lowercase()
    }

    fun validateStakeAmount(amount: Double): Double {
        if (amount <= 0) {
            throw ChaincodeException("Stake amount must be positive", "VALIDATION_ERROR")
        }
        if (amount < 1000.0) { // Minimum stake requirement
            throw ChaincodeException("Minimum stake amount is 1000", "INSUFFICIENT_STAKE")
        }
        return amount
    }

    fun validateRequired(value: String?, fieldName: String): String {
        if (value.isNullOrBlank()) {
            throw ChaincodeException("$fieldName is required", "VALIDATION_ERROR")
        }
        return value.trim()
    }

    fun validateStringLength(value: String?, fieldName: String, minLength: Int, maxLength: Int): String {
        val validated = validateRequired(value, fieldName)
        if (validated.length < minLength || validated.length > maxLength) {
            throw ChaincodeException(
                "$fieldName must be between $minLength and $maxLength characters",
                "VALIDATION_ERROR"
            )
        }
        return validated
    }
}