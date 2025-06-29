package org.degreechain.employer.models

import org.degreechain.common.models.PaymentStatus
import java.time.LocalDateTime

data class PaymentRecord(
    val paymentId: String,
    val certificateNumber: String,
    val organizationName: String,
    val amount: Double,
    val paymentMethod: String,
    val status: PaymentStatus,
    val paymentTimestamp: LocalDateTime,
    val transactionId: String,
    val fees: Double,
    val refundAmount: Double,
    val metadata: Map<String, Any>
)