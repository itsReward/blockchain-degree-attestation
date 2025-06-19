package org.degreechain.common.exceptions

import org.degreechain.common.models.ErrorCode

open class BusinessException(
    override val message: String,
    open val errorCode: ErrorCode,
    override val cause: Throwable? = null
) : Exception(message, cause) {

    constructor(message: String, errorCode: ErrorCode, details: Map<String, Any>) : this(message, errorCode) {
        this.details = details
    }

    var details: Map<String, Any> = emptyMap()
        private set

    override fun toString(): String {
        return "BusinessException(message='$message', errorCode=$errorCode, details=$details)"
    }
}