package org.degreechain.authority.controllers

import mu.KotlinLogging
import org.degreechain.authority.services.RevenueService
import org.degreechain.common.models.ApiResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/revenue")
@PreAuthorize("hasRole('ATTESTATION_AUTHORITY')")
class RevenueController(
    private val revenueService: RevenueService
) {

    @GetMapping("/summary")
    suspend fun getRevenueSummary(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val summary = revenueService.getRevenueSummary()
            ResponseEntity.ok(ApiResponse.success(summary, "Revenue summary retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get revenue summary" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @GetMapping("/university/{universityCode}")
    suspend fun getUniversityRevenue(
        @PathVariable universityCode: String,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val revenue = revenueService.getUniversityRevenue(universityCode, startDate, endDate)
            ResponseEntity.ok(ApiResponse.success(revenue, "University revenue retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get university revenue: $universityCode" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @PostMapping("/distribute")
    suspend fun distributeRevenue(): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = revenueService.distributeRevenue()
            ResponseEntity.ok(ApiResponse.success(result, "Revenue distribution initiated"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to distribute revenue" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping("/payments")
    suspend fun getPaymentHistory(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) paymentType: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val payments = revenueService.getPaymentHistory(page, size, paymentType)
            ResponseEntity.ok(ApiResponse.success(payments, "Payment history retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get payment history" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }
}