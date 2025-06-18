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
        status: PaymentStatus?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving payment history for organization: $organizationName" }

        val allPayments = paymentRecords.values
            .filter { it.organizationName == organizationName }
            .let { payments ->
                if (status != null) {
                    payments.filter { it.status == status }
                } else {
                    payments
                }
            }
            .sortedByDescending { it.createdAt }

        val startIndex = page * size
        val endIndex = minOf(startIndex + size, allPayments.size)
        val paginatedPayments = if (startIndex < allPayments.size) {
            allPayments.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        mapOf(
            "payments" to paginatedPayments,
            "page" to page,
            "size" to size,
            "totalElements" to allPayments.size,
            "totalPages" to (allPayments.size + size - 1) / size,
            "hasNext" to (endIndex < allPayments.size),
            "hasPrevious" to (page > 0),
            "filterStatus" to status?.name
        )
    }

    suspend fun refundPayment(paymentId: String, reason: String): PaymentResult = withContext(Dispatchers.IO) {
        logger.info { "Processing refund for payment: $paymentId, reason: $reason" }

        val existingRecord = paymentRecords[paymentId]
            ?: throw PaymentException(
                "Payment record not found: $paymentId",
                ErrorCode.RESOURCE_NOT_FOUND,
                paymentId = paymentId
            )

        if (existingRecord.status != PaymentStatus.COMPLETED) {
            throw PaymentException(
                "Cannot refund payment that is not completed",
                ErrorCode.PAYMENT_FAILED,
                paymentId = paymentId
            )
        }

        try {
            // Process refund based on original payment method
            val refundTransactionId = generateTransactionId("REFUND_${existingRecord.paymentMethod}")

            val refundedRecord = existingRecord.copy(
                status = PaymentStatus.REFUNDED,
                updatedAt = LocalDateTime.now(),
                failureReason = reason,
                refundTransactionId = refundTransactionId
            )

            paymentRecords[paymentId] = refundedRecord

            logger.info { "Refund processed successfully: $paymentId" }
            PaymentResult(
                success = true,
                paymentId = paymentId,
                transactionId = refundTransactionId
            )

        } catch (e: Exception) {
            logger.error(e) { "Refund processing failed for payment: $paymentId" }
            PaymentResult(
                success = false,
                paymentId = paymentId,
                transactionId = null,
                errorMessage = "Refund failed: ${e.message}"
            )
        }
    }

    suspend fun getPaymentSummary(organizationName: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Generating payment summary for organization: $organizationName" }

        val organizationPayments = paymentRecords.values
            .filter { it.organizationName == organizationName }

        val totalPayments = organizationPayments.size
        val completedPayments = organizationPayments.count { it.status == PaymentStatus.COMPLETED }
        val failedPayments = organizationPayments.count { it.status == PaymentStatus.FAILED }
        val refundedPayments = organizationPayments.count { it.status == PaymentStatus.REFUNDED }

        val totalAmount = organizationPayments
            .filter { it.status == PaymentStatus.COMPLETED }
            .sumOf { it.amount }

        val refundedAmount = organizationPayments
            .filter { it.status == PaymentStatus.REFUNDED }
            .sumOf { it.amount }

        val successRate = if (totalPayments > 0) {
            completedPayments.toDouble() / totalPayments
        } else {
            0.0
        }

        val averagePayment = if (completedPayments > 0) {
            totalAmount / completedPayments
        } else {
            0.0
        }

        val paymentMethodBreakdown = organizationPayments
            .groupBy { it.paymentMethod }
            .mapValues { it.value.size }

        mapOf(
            "organizationName" to organizationName,
            "totalPayments" to totalPayments,
            "completedPayments" to completedPayments,
            "failedPayments" to failedPayments,
            "refundedPayments" to refundedPayments,
            "totalAmount" to totalAmount,
            "refundedAmount" to refundedAmount,
            "netAmount" to (totalAmount - refundedAmount),
            "successRate" to successRate,
            "averagePayment" to averagePayment,
            "paymentMethodBreakdown" to paymentMethodBreakdown,
            "lastPaymentDate" to organizationPayments
                .maxByOrNull { it.createdAt }?.createdAt
        )
    }

    private suspend fun processCreditCardPayment(record: PaymentRecord): PaymentRecord = withContext(Dispatchers.IO) {
        logger.debug { "Processing credit card payment: ${record.paymentId}" }

        // Simulate credit card processing
        Thread.sleep(100) // Simulate network delay

        // Simulate 95% success rate
        val success = Math.random() > 0.05

        if (success) {
            record.copy(
                status = PaymentStatus.COMPLETED,
                updatedAt = LocalDateTime.now(),
                processingDetails = mapOf(
                    "cardType" to "VISA",
                    "lastFourDigits" to "1234",
                    "authorizationCode" to generateAuthCode()
                )
            )
        } else {
            record.copy(
                status = PaymentStatus.FAILED,
                updatedAt = LocalDateTime.now(),
                failureReason = "Credit card declined"
            )
        }
    }

    private suspend fun processBankTransferPayment(record: PaymentRecord): PaymentRecord = withContext(Dispatchers.IO) {
        logger.debug { "Processing bank transfer payment: ${record.paymentId}" }

        // Bank transfers typically take longer to process
        Thread.sleep(200)

        // Simulate 98% success rate
        val success = Math.random() > 0.02

        if (success) {
            record.copy(
                status = PaymentStatus.COMPLETED,
                updatedAt = LocalDateTime.now(),
                processingDetails = mapOf(
                    "bankReference" to "BT${System.currentTimeMillis()}",
                    "clearingCode" to "ACH001"
                )
            )
        } else {
            record.copy(
                status = PaymentStatus.FAILED,
                updatedAt = LocalDateTime.now(),
                failureReason = "Bank transfer failed - insufficient funds"
            )
        }
    }

    private suspend fun processCryptoPayment(record: PaymentRecord): PaymentRecord = withContext(Dispatchers.IO) {
        logger.debug { "Processing crypto payment: ${record.paymentId}" }

        // Crypto payments can be slower due to blockchain confirmation
        Thread.sleep(300)

        // Simulate 90% success rate (crypto can be more volatile)
        val success = Math.random() > 0.10

        if (success) {
            record.copy(
                status = PaymentStatus.COMPLETED,
                updatedAt = LocalDateTime.now(),
                processingDetails = mapOf(
                    "cryptocurrency" to "USDC",
                    "blockchainTxHash" to "0x${generateRandomHex(64)}",
                    "confirmations" to "6"
                )
            )
        } else {
            record.copy(
                status = PaymentStatus.FAILED,
                updatedAt = LocalDateTime.now(),
                failureReason = "Crypto transaction failed - network congestion"
            )
        }
    }

    private fun generateTransactionId(paymentMethod: String): String {
        val prefix = when (paymentMethod.uppercase()) {
            "CREDIT_CARD" -> "CC"
            "BANK_TRANSFER" -> "BT"
            "CRYPTO" -> "CR"
            else -> "TX"
        }
        return "${prefix}${System.currentTimeMillis()}"
    }

    private fun generateAuthCode(): String {
        return (100000..999999).random().toString()
    }

    private fun generateRandomHex(length: Int): String {
        val chars = "0123456789abcdef"
        return (1..length)
            .map { chars.random() }
            .joinToString("")
    }

    // Method for getting standard verification fee
    suspend fun getVerificationFee(): Double = withContext(Dispatchers.IO) {
        standardVerificationFee
    }

    // Method for validating payment before processing
    suspend fun validatePayment(
        amount: Double,
        paymentMethod: String,
        organizationName: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            when {
                amount < standardVerificationFee -> {
                    logger.warn { "Payment amount $amount is below minimum $standardVerificationFee" }
                    false
                }
                paymentMethod.uppercase() !in setOf("CREDIT_CARD", "BANK_TRANSFER", "CRYPTO") -> {
                    logger.warn { "Invalid payment method: $paymentMethod" }
                    false
                }
                organizationName.isBlank() -> {
                    logger.warn { "Organization name is required" }
                    false
                }
                else -> true
            }
        } catch (e: Exception) {
            logger.error(e) { "Error validating payment" }
            false
        }
    }
}