package org.degreechain.employer.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.PaymentException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.models.PaymentStatus
import org.degreechain.employer.models.PaymentRecord
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class PaymentResult(
    val success: Boolean,
    val paymentId: String?,
    val transactionId: String?,
    val errorMessage: String? = null
)

@Service
class PaymentService {

    // In-memory storage for demo purposes - in production this would be a database
    private val paymentRecords = ConcurrentHashMap<String, PaymentRecord>()

    // Standard verification fee
    private val standardVerificationFee = 10.0

    suspend fun processVerificationPayment(
        amount: Double,
        paymentMethod: String,
        certificateNumber: String,
        organizationName: String? = null
    ): PaymentResult = withContext(Dispatchers.IO) {
        logger.info { "Processing verification payment: $amount via $paymentMethod for certificate $certificateNumber" }

        try {
            // Validate payment amount
            if (amount < standardVerificationFee) {
                throw PaymentException(
                    "Insufficient payment amount. Minimum required: $standardVerificationFee",
                    ErrorCode.INSUFFICIENT_FUNDS,
                    amount = amount,
                    currency = "USD"
                )
            }

            val paymentId = UUID.randomUUID().toString()
            val transactionId = generateTransactionId(paymentMethod)

            // Create payment record
            val paymentRecord = PaymentRecord(
                paymentId = paymentId,
                amount = amount,
                currency = "USD",
                paymentMethod = paymentMethod,
                status = PaymentStatus.PROCESSING,
                organizationName = organizationName ?: "Unknown",
                certificateNumber = certificateNumber,
                createdAt = LocalDateTime.now(),
                transactionId = transactionId
            )

            paymentRecords[paymentId] = paymentRecord

            // Process payment based on method
            val processedRecord = when (paymentMethod.uppercase()) {
                "CREDIT_CARD" -> processCreditCardPayment(paymentRecord)
                "BANK_TRANSFER" -> processBankTransferPayment(paymentRecord)
                "CRYPTO" -> processCryptoPayment(paymentRecord)
                else -> throw PaymentException(
                    "Unsupported payment method: $paymentMethod",
                    ErrorCode.INVALID_PAYMENT_METHOD
                )
            }

            // Update record
            paymentRecords[paymentId] = processedRecord

            if (processedRecord.status == PaymentStatus.COMPLETED) {
                logger.info { "Payment processed successfully: $paymentId" }
                PaymentResult(
                    success = true,
                    paymentId = paymentId,
                    transactionId = transactionId
                )
            } else {
                logger.warn { "Payment failed: $paymentId - ${processedRecord.failureReason}" }
                PaymentResult(
                    success = false,
                    paymentId = paymentId,
                    transactionId = transactionId,
                    errorMessage = processedRecord.failureReason
                )
            }

        } catch (e: PaymentException) {
            logger.error(e) { "Payment processing failed" }
            PaymentResult(
                success = false,
                paymentId = null,
                transactionId = null,
                errorMessage = e.message
            )
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during payment processing" }
            PaymentResult(
                success = false,
                paymentId = null,
                transactionId = null,
                errorMessage = "Payment processing failed due to technical error"
            )
        }
    }

    suspend fun getPaymentStatus(paymentId: String): PaymentRecord = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving payment status for: $paymentId" }

        val record = paymentRecords[paymentId]
            ?: throw PaymentException(
                "Payment record not found: $paymentId",
                ErrorCode.RESOURCE_NOT_FOUND,
                paymentId = paymentId
            )

        record
    }

    suspend fun getPaymentHistory(
        organizationName: String,
        page: Int,
        size: Int,
        status: PaymentStatus? = null
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving payment history for organization: $organizationName" }

        val allPayments = paymentRecords.values.filter { payment ->
            payment.organizationName == organizationName &&
                    (status == null || payment.status == status)
        }.toList()

        // Sort by creation date (newest first)
        val sortedPayments = allPayments.sortedByDescending { it.createdAt }

        // Apply pagination
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, sortedPayments.size)
        val paginatedPayments = if (startIndex < sortedPayments.size) {
            sortedPayments.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        // Generate statistics
        val totalAmount = allPayments.sumOf { it.amount }
        val statusBreakdown = allPayments.groupBy { it.status }
            .mapValues { (_, payments) -> payments.size }
        val methodBreakdown = allPayments.groupBy { it.paymentMethod }
            .mapValues { (_, payments) -> payments.size }

        mapOf(
            "payments" to paginatedPayments,
            "page" to page,
            "size" to size,
            "totalElements" to allPayments.size,
            "totalPages" to (allPayments.size + size - 1) / size,
            "hasNext" to (endIndex < allPayments.size),
            "hasPrevious" to (page > 0),
            "statistics" to mapOf(
                "totalAmount" to totalAmount,
                "statusBreakdown" to statusBreakdown,
                "methodBreakdown" to methodBreakdown,
                "averageAmount" to if (allPayments.isNotEmpty()) totalAmount / allPayments.size else 0.0
            )
        )
    }

    private fun generateTransactionId(paymentMethod: String): String {
        val prefix = when (paymentMethod.uppercase()) {
            "CREDIT_CARD" -> "CC"
            "BANK_TRANSFER" -> "BT"
            "CRYPTO" -> "CR"
            else -> "TX"
        }
        return "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}"
    }

    private fun processCreditCardPayment(record: PaymentRecord): PaymentRecord {
        // Simulate credit card processing
        logger.debug { "Processing credit card payment: ${record.paymentId}" }

        // Simulate processing delay and random success/failure
        return if (Math.random() > 0.1) { // 90% success rate
            record.copy(
                status = PaymentStatus.COMPLETED,
                updatedAt = LocalDateTime.now()
            )
        } else {
            record.copy(
                status = PaymentStatus.FAILED,
                failureReason = "Credit card declined",
                updatedAt = LocalDateTime.now()
            )
        }
    }

    private fun processBankTransferPayment(record: PaymentRecord): PaymentRecord {
        // Simulate bank transfer processing
        logger.debug { "Processing bank transfer payment: ${record.paymentId}" }

        return record.copy(
            status = PaymentStatus.COMPLETED,
            updatedAt = LocalDateTime.now()
        )
    }

    private fun processCryptoPayment(record: PaymentRecord): PaymentRecord {
        // Simulate crypto payment processing
        logger.debug { "Processing crypto payment: ${record.paymentId}" }

        return record.copy(
            status = PaymentStatus.COMPLETED,
            updatedAt = LocalDateTime.now()
        )
    }
}