package org.degreechain.common.exceptions

import org.degreechain.common.models.ErrorCode

class PaymentException(
    override val message: String,
    override val errorCode: ErrorCode = ErrorCode.PAYMENT_FAILED,
    val paymentId: String? = null,
    val amount: Double? = null,
    val currency: String? = null,
    override val cause: Throwable? = null
) : BusinessException(message, errorCode, cause) {

    override fun toString(): String {
        return "PaymentException(message='$message', errorCode=$errorCode, " +
                "paymentId=$paymentId, amount=$amount, currency=$currency)"
    }
}