package org.degreechain.common.models

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val message: String? = null,
    val errorCode: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val requestId: String? = null
) {
    companion object {
        fun <T> success(data: T, message: String? = null): ApiResponse<T> {
            return ApiResponse(success = true, data = data, message = message)
        }

        fun <T> error(message: String, errorCode: String? = null): ApiResponse<T> {
            return ApiResponse(success = false, message = message, errorCode = errorCode)
        }

        fun <T> error(exception: Exception): ApiResponse<T> {
            return ApiResponse(
                success = false,
                message = exception.message ?: "Unknown error",
                errorCode = exception.javaClass.simpleName
            )
        }
    }
}

/**
 * Error details for API responses
 */
data class ErrorDetails(
    val code: String,
    val message: String,
    val details: Map<String, Any>? = null
)