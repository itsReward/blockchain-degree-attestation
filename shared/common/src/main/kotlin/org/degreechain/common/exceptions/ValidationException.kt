package org.degreechain.common.exceptions

import org.degreechain.common.models.ErrorCode

class ValidationException(
    override val message: String,
    val field: String? = null,
    val value: Any? = null,
    override val cause: Throwable? = null
) : BusinessException(message, ErrorCode.VALIDATION_ERROR, cause) {

    val validationErrors: MutableList<ValidationError> = mutableListOf()

    constructor(errors: List<ValidationError>) : this("Validation failed") {
        validationErrors.addAll(errors)
    }

    fun addError(field: String, message: String, value: Any? = null) {
        validationErrors.add(ValidationError(field, message, value))
    }

    override fun toString(): String {
        return "ValidationException(message='$message', field=$field, value=$value, errors=$validationErrors)"
    }
}

data class ValidationError(
    val field: String,
    val message: String,
    val value: Any? = null
)