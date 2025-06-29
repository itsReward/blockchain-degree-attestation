package org.degreechain.employer.controllers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.common.models.PaymentStatus
import org.degreechain.employer.models.PaymentRecord
import org.degreechain.employer.services.PaymentService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

/**
 * Controller for handling payment operations in the employer portal
 * Manages verification payments, payment history, and refund processing
 */
@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('EMPLOYER')")
@Validated
class PaymentController(
    private val paymentService: PaymentService
) {

    /**
     * Process payment for degree verification
     */
    @PostMapping("/verification")
    suspend fun processVerificationPayment(
        @Valid @RequestBody request: PaymentRequest
    ): ResponseEntity<ApiResponse<PaymentRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Processing verification payment for certificate: ${request.certificateNumber}" }

            val paymentResult = paymentService.processVerificationPayment(
                amount = request.amount,
                paymentMethod = request.paymentMethod,
                certificateNumber = request.certificateNumber,
                organizationName = request.organizationName
            )

            if (paymentResult.success) {
                val paymentRecord = paymentService.getPaymentRecord(paymentResult.paymentId!!)
                ResponseEntity.ok(
                    ApiResponse.success(
                        paymentRecord,
                        "Payment processed successfully"
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(
                        ApiResponse.error<PaymentRecord>(
                            "Payment failed: ${paymentResult.errorMessage}"
                        )
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process verification payment" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<PaymentRecord>("Payment processing failed: ${e.message}"))
        }
    }

    /**
     * Get payment record by ID
     */
    @GetMapping("/{paymentId}")
    suspend fun getPaymentRecord(
        @PathVariable @NotBlank paymentId: String
    ): ResponseEntity<ApiResponse<PaymentRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving payment record: $paymentId" }

            val paymentRecord = paymentService.getPaymentRecord(paymentId)
            ResponseEntity.ok(
                ApiResponse.success(
                    paymentRecord,
                    "Payment record retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment record: $paymentId" }
            ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error<PaymentRecord>("Payment record not found"))
        }
    }

    /**
     * Get payment history for an organization
     */
    @GetMapping("/history")
    suspend fun getPaymentHistory(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") @Min(1) size: Int,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<List<PaymentRecord>>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving payment history for organization: $organizationName" }

            val paymentHistory = paymentService.getPaymentHistory(
                organizationName = organizationName,
                page = page,
                size = size,
                status = status,
                startDate = startDate,
                endDate = endDate
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    paymentHistory,
                    "Payment history retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment history for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<List<PaymentRecord>>("Failed to retrieve payment history"))
        }
    }

    /**
     * Get payment summary/analytics for an organization
     */
    @GetMapping("/summary")
    suspend fun getPaymentSummary(
        @RequestParam @NotBlank organizationName: String,
        @RequestParam(required = false) startDate: String?,
        @RequestParam(required = false) endDate: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Retrieving payment summary for organization: $organizationName" }

            val summary = paymentService.getPaymentSummary(
                organizationName = organizationName,
                startDate = startDate,
                endDate = endDate
            )

            ResponseEntity.ok(
                ApiResponse.success(
                    summary,
                    "Payment summary retrieved successfully"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment summary for: $organizationName" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<Map<String, Any>>("Failed to retrieve payment summary"))
        }
    }

    /**
     * Process refund for a payment
     */
    @PostMapping("/{paymentId}/refund")
    suspend fun processRefund(
        @PathVariable @NotBlank paymentId: String,
        @Valid @RequestBody refundRequest: RefundRequest
    ): ResponseEntity<ApiResponse<PaymentRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Processing refund for payment: $paymentId" }

            val refundResult = paymentService.processRefund(
                paymentId = paymentId,
                reason = refundRequest.reason,
                refundAmount = refundRequest.refundAmount
            )

            if (refundResult.success) {
                val updatedPaymentRecord = paymentService.getPaymentRecord(paymentId)
                ResponseEntity.ok(
                    ApiResponse.success(
                        updatedPaymentRecord,
                        "Refund processed successfully"
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                        ApiResponse.error<PaymentRecord>(
                            "Refund failed: ${refundResult.errorMessage}"
                        )
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to process refund for payment: $paymentId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<PaymentRecord>("Refund processing failed: ${e.message}"))
        }
    }

    /**
     * Cancel a pending payment
     */
    @PostMapping("/{paymentId}/cancel")
    suspend fun cancelPayment(
        @PathVariable @NotBlank paymentId: String,
        @Valid @RequestBody cancelRequest: CancelPaymentRequest
    ): ResponseEntity<ApiResponse<PaymentRecord>> = withContext(Dispatchers.IO) {
        return@withContext try {
            logger.info { "Cancelling payment: $paymentId" }

            val cancelResult = paymentService.cancelPayment(
                paymentId = paymentId,
                reason = cancelRequest.reason
            )

            if (cancelResult.success) {
                val updatedPaymentRecord = paymentService.getPaymentRecord(paymentId)
                ResponseEntity.ok(
                    ApiResponse.success(
                        updatedPaymentRecord,
                        "Payment cancelled successfully"
                    )
                )
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(
                        ApiResponse.error<PaymentRecord>(
                            "Payment cancellation failed: ${cancelResult.errorMessage}"
                        )
                    )
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to cancel payment: $paymentId" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<PaymentRecord>("Payment cancellation failed: ${e.message}"))
        }
    }

    /**
     * Get supported payment methods
     */
    @GetMapping("/methods")
    suspend fun getSupportedPaymentMethods(): ResponseEntity<ApiResponse<List<PaymentMethodInfo>>> {
        return try {
            val paymentMethods = paymentService.getSupportedPaymentMethods()
            ResponseEntity.ok(
                ApiResponse.success(
                    paymentMethods,
                    "Supported payment methods retrieved"
                )
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to get supported payment methods" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error<List<PaymentMethodInfo>>("Failed to retrieve payment methods"))
        }
    }
}

/**
 * Request model for payment processing
 */
data class PaymentRequest(
    @field:NotBlank(message = "Certificate number is required")
    val certificateNumber: String,

    @field:Min(value = 1, message = "Amount must be positive")
    val amount: Double,

    @field:NotBlank(message = "Payment method is required")
    @field:Pattern(
        regexp = "^(CREDIT_CARD|BANK_TRANSFER|CRYPTO)$",
        message = "Invalid payment method"
    )
    val paymentMethod: String,

    @field:NotBlank(message = "Organization name is required")
    val organizationName: String,

    val additionalInfo: Map<String, Any> = emptyMap()
)

/**
 * Request model for refund processing
 */
data class RefundRequest(
    @field:NotBlank(message = "Refund reason is required")
    val reason: String,

    @field:Min(value = 0, message = "Refund amount cannot be negative")
    val refundAmount: Double? = null // null means full refund
)

/**
 * Request model for payment cancellation
 */
data class CancelPaymentRequest(
    @field:NotBlank(message = "Cancellation reason is required")
    val reason: String
)

/**
 * Information about supported payment methods
 */
data class PaymentMethodInfo(
    val method: String,
    val displayName: String,
    val description: String,
    val minimumAmount: Double,
    val maximumAmount: Double?,
    val processingTime: String,
    val fees: Map<String, Any>,
    val enabled: Boolean
)