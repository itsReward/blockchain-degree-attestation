package org.degreechain.employer.models

import com.fasterxml.jackson.annotation.JsonFormat
import org.degreechain.common.models.PaymentStatus
import java.time.LocalDateTime

data class PaymentRecord(
    val paymentId: String,
    val amount: Double,
    val currency: String,
    val paymentMethod: String,
    val status: PaymentStatus,
    val organizationName: String,
    val certificateNumber: String,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val createdAt: LocalDateTime,

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    val updatedAt: LocalDateTime = LocalDateTime.now(),

    val transactionId: String? = null,
    val failureReason: String? = null,
    val metadata: Map<String, Any> = emptyMap()
) {
}