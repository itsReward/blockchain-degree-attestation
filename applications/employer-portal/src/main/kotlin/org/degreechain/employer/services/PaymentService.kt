package org.degreechain.employer.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.models.PaymentStatus
import org.degreechain.common.utils.ValidationUtils
import org.degreechain.employer.models.PaymentRecord
import org.degreechain.employer.controllers.PaymentMethodInfo
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import kotlin.math.max

private val logger = KotlinLogging.logger {}

/**
 * Result model for payment processing
 */
data class PaymentResult(
    val success: Boolean,
    val paymentId: String?,
    val errorMessage: String?
)

@Service
class PaymentService {

    /**
     * Process verification payment
     */
    suspend fun processVerificationPayment(
        amount: Double,
        paymentMethod: String,
        certificateNumber: String,
        organizationName: String
    ): PaymentResult = withContext(Dispatchers.IO) {
        logger.info { "Processing verification payment: $amount via $paymentMethod for certificate: $certificateNumber" }

        try {
            // Validate payment parameters
            if (amount <= 0) {
                throw BusinessException("Payment amount must be positive", ErrorCode.VALIDATION_ERROR)
            }

            val validMethods = setOf("CREDIT_CARD", "BANK_TRANSFER", "CRYPTO")
            if (paymentMethod !in validMethods) {
                throw BusinessException("Invalid payment method", ErrorCode.VALIDATION_ERROR)
            }

            // Simulate payment processing
            val paymentId = "PAY-${UUID.randomUUID().toString().substring(0, 12)}"

            // Simulate payment success/failure (90% success rate)
            val success = (1..10).random() <= 9

            if (success) {
                PaymentResult(
                    success = true,
                    paymentId = paymentId,
                    errorMessage = null
                )
            } else {
                PaymentResult(
                    success = false,
                    paymentId = null,
                    errorMessage = "Payment declined by provider"
                )
            }

        } catch (e: Exception) {
            logger.error(e) { "Payment processing failed" }
            PaymentResult(
                success = false,
                paymentId = null,
                errorMessage = e.message ?: "Payment processing failed"
            )
        }
    }

    /**
     * Get payment record by ID
     */
    suspend fun getPaymentRecord(paymentId: String): PaymentRecord = withContext(Dispatchers.IO) {
        logger.info { "Retrieving payment record: $paymentId" }

        try {
            ValidationUtils.validateRequired(paymentId, "Payment ID")

            // Mock payment record
            PaymentRecord(
                paymentId = paymentId,
                certificateNumber = "CERT-${UUID.randomUUID().toString().substring(0, 8)}",
                organizationName = "TechCorp",
                amount = 10.0,
                paymentMethod = "CREDIT_CARD",
                status = PaymentStatus.COMPLETED,
                paymentTimestamp = LocalDateTime.now(),
                transactionId = "TXN-${UUID.randomUUID().toString().substring(0, 10)}",
                fees = 0.29,
                refundAmount = 0.0,
                metadata = mapOf("verificationId" to "VER-123")
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment record: $paymentId" }
            throw BusinessException(
                "Payment record not found",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    /**
     * Get payment history for organization
     */
    suspend fun getPaymentHistory(
        organizationName: String,
        page: Int,
        size: Int,
        status: PaymentStatus?,
        startDate: String?,
        endDate: String?
    ): List<PaymentRecord> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving payment history for organization: $organizationName" }

        try {
            ValidationUtils.validateRequired(organizationName, "Organization Name")

            // Generate mock payment history
            val mockHistory = generateMockPaymentHistory(organizationName, status)

            // Apply date filters
            var filteredHistory = mockHistory.toList()

            if (!startDate.isNullOrBlank()) {
                val start = LocalDateTime.parse(startDate)
                filteredHistory = filteredHistory.filter { it.paymentTimestamp.isAfter(start) }
            }

            if (!endDate.isNullOrBlank()) {
                val end = LocalDateTime.parse(endDate)
                filteredHistory = filteredHistory.filter { it.paymentTimestamp.isBefore(end) }
            }

            // Apply pagination
            val offset = page * size
            filteredHistory.drop(offset).take(size)

        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment history for: $organizationName" }
            throw BusinessException(
                "Failed to retrieve payment history",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Get payment summary/analytics
     */
    suspend fun getPaymentSummary(
        organizationName: String,
        startDate: String?,
        endDate: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving payment summary for organization: $organizationName" }

        try {
            ValidationUtils.validateRequired(organizationName, "Organization Name")

            val mockHistory = generateMockPaymentHistory(organizationName, null)

            // Apply date filters if provided
            val filteredHistory = mockHistory.filter { payment ->
                val timestamp = payment.paymentTimestamp
                val afterStart = startDate?.let { timestamp.isAfter(LocalDateTime.parse(it)) } ?: true
                val beforeEnd = endDate?.let { timestamp.isBefore(LocalDateTime.parse(it)) } ?: true
                afterStart && beforeEnd
            }

            val totalPayments = filteredHistory.size
            val totalAmount = filteredHistory.sumOf { it.amount }
            val totalFees = filteredHistory.sumOf { it.fees }
            val totalRefunds = filteredHistory.sumOf { it.refundAmount }
            val completedPayments = filteredHistory.count { it.status == PaymentStatus.COMPLETED }
            val failedPayments = filteredHistory.count { it.status == PaymentStatus.FAILED }
            val pendingPayments = filteredHistory.count { it.status == PaymentStatus.PENDING }

            // Payment method breakdown
            val paymentMethodBreakdown = filteredHistory.groupBy { it.paymentMethod }
                .mapValues { (_, payments) ->
                    mapOf(
                        "count" to payments.size,
                        "totalAmount" to payments.sumOf { it.amount },
                        "averageAmount" to if (payments.isNotEmpty()) payments.sumOf { it.amount } / payments.size else 0.0
                    )
                }

            // Monthly trend (last 12 months)
            val monthlyTrend = (0..11).map { monthsAgo ->
                val targetMonth = LocalDateTime.now().minusMonths(monthsAgo.toLong())
                val monthPayments = filteredHistory.filter {
                    it.paymentTimestamp.year == targetMonth.year &&
                            it.paymentTimestamp.month == targetMonth.month
                }
                mapOf(
                    "month" to "${targetMonth.year}-${targetMonth.monthValue.toString().padStart(2, '0')}",
                    "count" to monthPayments.size,
                    "amount" to monthPayments.sumOf { it.amount }
                )
            }.reversed()

            mapOf(
                "summary" to mapOf(
                    "totalPayments" to totalPayments,
                    "totalAmount" to totalAmount,
                    "totalFees" to totalFees,
                    "totalRefunds" to totalRefunds,
                    "completedPayments" to completedPayments,
                    "failedPayments" to failedPayments,
                    "pendingPayments" to pendingPayments,
                    "successRate" to if (totalPayments > 0) (completedPayments.toDouble() / totalPayments) * 100 else 0.0,
                    "averagePaymentAmount" to if (totalPayments > 0) totalAmount / totalPayments else 0.0
                ),
                "paymentMethodBreakdown" to paymentMethodBreakdown,
                "monthlyTrend" to monthlyTrend,
                "period" to mapOf(
                    "startDate" to startDate,
                    "endDate" to endDate
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment summary for: $organizationName" }
            throw BusinessException(
                "Failed to retrieve payment summary",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Request refund for a payment
     */
    suspend fun requestRefund(
        paymentId: String,
        reason: String,
        refundAmount: Double?
    ): PaymentRecord = withContext(Dispatchers.IO) {
        logger.info { "Processing refund request for payment: $paymentId" }

        try {
            ValidationUtils.validateRequired(paymentId, "Payment ID")
            ValidationUtils.validateRequired(reason, "Refund Reason")

            // Get original payment record
            val originalPayment = getPaymentRecord(paymentId)

            // Calculate refund amount
            val actualRefundAmount = refundAmount ?: originalPayment.amount

            if (actualRefundAmount > originalPayment.amount) {
                throw BusinessException(
                    "Refund amount cannot exceed original payment amount",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            if (originalPayment.status != PaymentStatus.COMPLETED) {
                throw BusinessException(
                    "Can only refund completed payments",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            // Process refund (simulate)
            val refundedPayment = originalPayment.copy(
                status = PaymentStatus.REFUNDED,
                refundAmount = actualRefundAmount,
                metadata = originalPayment.metadata + mapOf(
                    "refundReason" to reason,
                    "refundDate" to LocalDateTime.now().toString(),
                    "refundId" to "REF-${UUID.randomUUID().toString().substring(0, 8)}"
                )
            )

            logger.info { "Refund processed successfully for payment: $paymentId" }
            refundedPayment

        } catch (e: Exception) {
            logger.error(e) { "Failed to process refund for payment: $paymentId" }
            throw BusinessException(
                "Refund processing failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Cancel a pending payment
     */
    suspend fun cancelPayment(
        paymentId: String,
        reason: String
    ): PaymentRecord = withContext(Dispatchers.IO) {
        logger.info { "Cancelling payment: $paymentId" }

        try {
            ValidationUtils.validateRequired(paymentId, "Payment ID")
            ValidationUtils.validateRequired(reason, "Cancellation Reason")

            // Get original payment record
            val originalPayment = getPaymentRecord(paymentId)

            if (originalPayment.status != PaymentStatus.PENDING) {
                throw BusinessException(
                    "Can only cancel pending payments",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            // Cancel payment
            val cancelledPayment = originalPayment.copy(
                status = PaymentStatus.CANCELLED,
                metadata = originalPayment.metadata + mapOf(
                    "cancellationReason" to reason,
                    "cancellationDate" to LocalDateTime.now().toString()
                )
            )

            logger.info { "Payment cancelled successfully: $paymentId" }
            cancelledPayment

        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel payment: $paymentId" }
            throw BusinessException(
                "Payment cancellation failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    /**
     * Get supported payment methods
     */
    suspend fun getSupportedPaymentMethods(): List<PaymentMethodInfo> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving supported payment methods" }

        try {
            listOf(
                PaymentMethodInfo(
                    method = "CREDIT_CARD",
                    displayName = "Credit Card",
                    description = "Pay with Visa, MasterCard, or American Express",
                    minimumAmount = 1.0,
                    maximumAmount = 10000.0,
                    processingTime = "Instant",
                    fees = mapOf(
                        "percentage" to 2.9,
                        "fixed" to 0.30
                    ),
                    enabled = true
                ),
                PaymentMethodInfo(
                    method = "BANK_TRANSFER",
                    displayName = "Bank Transfer",
                    description = "Direct transfer from your bank account",
                    minimumAmount = 10.0,
                    maximumAmount = 50000.0,
                    processingTime = "1-3 business days",
                    fees = mapOf(
                        "percentage" to 1.0,
                        "minimum" to 2.0
                    ),
                    enabled = true
                ),
                PaymentMethodInfo(
                    method = "CRYPTO",
                    displayName = "Cryptocurrency",
                    description = "Pay with Bitcoin, Ethereum, or other cryptocurrencies",
                    minimumAmount = 5.0,
                    maximumAmount = null,
                    processingTime = "15-30 minutes",
                    fees = mapOf(
                        "percentage" to 1.5,
                        "networkFee" to "Variable"
                    ),
                    enabled = true
                )
            )

        } catch (e: Exception) {
            logger.error(e) { "Failed to get supported payment methods" }
            throw BusinessException(
                "Failed to retrieve payment methods",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    // Helper method for generating mock payment history
    private fun generateMockPaymentHistory(
        organizationName: String,
        status: PaymentStatus?
    ): List<PaymentRecord> {
        val statuses = listOf(PaymentStatus.COMPLETED, PaymentStatus.FAILED, PaymentStatus.PENDING, PaymentStatus.REFUNDED)
        val paymentMethods = listOf("CREDIT_CARD", "BANK_TRANSFER", "CRYPTO")

        return (1..30).map { i ->
            val paymentStatus = status ?: statuses.random()
            val random = java.util.Random()
            val amount = 5.0 + (20.0 - 5.0) * random.nextDouble()


            PaymentRecord(
                paymentId = "PAY-${organizationName}-${i.toString().padStart(4, '0')}",
                certificateNumber = "CERT-${i.toString().padStart(6, '0')}",
                organizationName = organizationName,
                amount = amount,
                paymentMethod = paymentMethods.random(),
                status = paymentStatus,
                paymentTimestamp = LocalDateTime.now().minusDays((0..90).random().toLong()),
                transactionId = "TXN-${UUID.randomUUID().toString().substring(0, 10)}",
                fees = amount * 0.029, // 2.9% fee
                refundAmount = if (paymentStatus == PaymentStatus.REFUNDED) amount else 0.0,
                metadata = mapOf(
                    "verificationId" to "VER-${i.toString().padStart(4, '0')}",
                    "processingTime" to "${(1..5).random()} seconds"
                )
            )
        }
    }
}