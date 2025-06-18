package org.degreechain.common.models

enum class PaymentStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    REFUNDED,
    CANCELLED
}