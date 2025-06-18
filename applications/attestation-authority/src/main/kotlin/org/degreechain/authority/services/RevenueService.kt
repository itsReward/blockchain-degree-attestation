package org.degreechain.authority.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.authority.models.RevenueReport
import org.degreechain.authority.models.UniversityRevenue
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class RevenueService(
    private val contractInvoker: ContractInvoker
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun getRevenueSummary(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving revenue summary" }

        try {
            // Get all universities to calculate total revenue
            val universitiesJson = contractInvoker.getAllUniversities()
            val universities: List<Map<String, Any>> = objectMapper.readValue(universitiesJson)

            val totalRevenue = universities.sumOf {
                (it["revenue"] as? Number)?.toDouble() ?: 0.0
            }
            val totalStake = universities.sumOf {
                (it["stakeAmount"] as? Number)?.toDouble() ?: 0.0
            }
            val activeUniversities = universities.count { it["status"] == "ACTIVE" }
            val totalUniversities = universities.size

            // Calculate authority share (assuming 50% of total revenue goes to authority)
            val authorityShare = totalRevenue * 0.5
            val universityTotalShare = totalRevenue * 0.5

            mapOf(
                "totalRevenue" to totalRevenue,
                "authorityShare" to authorityShare,
                "universityTotalShare" to universityTotalShare,
                "totalStakeAmount" to totalStake,
                "totalUniversities" to totalUniversities,
                "activeUniversities" to activeUniversities,
                "averageRevenuePerUniversity" to if (activeUniversities > 0) totalRevenue / activeUniversities else 0.0,
                "reportDate" to LocalDate.now().format(dateFormatter)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve revenue summary" }
            throw BusinessException(
                "Failed to retrieve revenue summary: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getUniversityRevenue(
        universityCode: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving revenue for university: $universityCode" }

        try {
            // Get university statistics
            val statsJson = contractInvoker.getUniversityStatistics(universityCode)
            val stats: Map<String, Any> = objectMapper.readValue(statsJson)

            // In a real implementation, we would filter by date range
            // For now, return all-time statistics
            val revenue = (stats["revenue"] as? Number)?.toDouble() ?: 0.0
            val degreesIssued = (stats["totalDegreesIssued"] as? Number)?.toLong() ?: 0L
            val averageRevenuePerDegree = if (degreesIssued > 0) revenue / degreesIssued else 0.0

            mapOf(
                "universityCode" to universityCode,
                "universityName" to stats["universityName"],
                "totalRevenue" to revenue,
                "totalDegreesIssued" to degreesIssued,
                "averageRevenuePerDegree" to averageRevenuePerDegree,
                "stakeAmount" to stats["stakeAmount"],
                "status" to stats["status"],
                "reportPeriodStart" to (startDate?.format(dateFormatter) ?: "All time"),
                "reportPeriodEnd" to (endDate?.format(dateFormatter) ?: "Present")
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve university revenue: $universityCode" }
            throw BusinessException(
                "Failed to retrieve university revenue: ${e.message}",
                ErrorCode.RESOURCE_NOT_FOUND,
                cause = e
            )
        }
    }

    suspend fun distributeRevenue(): String = withContext(Dispatchers.IO) {
        logger.info { "Initiating revenue distribution" }

        try {
            // Get all active universities
            val universitiesJson = contractInvoker.getAllUniversities()
            val universities: List<Map<String, Any>> = objectMapper.readValue(universitiesJson)

            val activeUniversities = universities.filter { it["status"] == "ACTIVE" }

            if (activeUniversities.isEmpty()) {
                return@withContext "No active universities found for revenue distribution"
            }

            val distributionRecords = mutableListOf<Map<String, Any>>()
            var totalDistributed = 0.0

            activeUniversities.forEach { university ->
                val universityCode = university["universityCode"] as String
                val revenue = (university["revenue"] as? Number)?.toDouble() ?: 0.0

                if (revenue > 0) {
                    distributionRecords.add(mapOf(
                        "universityCode" to universityCode,
                        "universityName" to university["universityName"],
                        "amount" to revenue,
                        "status" to "DISTRIBUTED"
                    ))
                    totalDistributed += revenue
                }
            }

            // In a real implementation, this would trigger actual payment transfers
            logger.info { "Revenue distribution completed. Total distributed: $totalDistributed to ${distributionRecords.size} universities" }

            "Revenue distribution completed successfully. Distributed $totalDistributed to ${distributionRecords.size} universities."
        } catch (e: Exception) {
            logger.error(e) { "Failed to distribute revenue" }
            throw BusinessException(
                "Revenue distribution failed: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getPaymentHistory(
        page: Int,
        size: Int,
        paymentType: String?
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving payment history - page: $page, size: $size, type: $paymentType" }

        try {
            // In a real implementation, this would query payment records from blockchain
            // For now, return mock data structure
            val mockPayments = generateMockPaymentHistory(paymentType)

            val startIndex = page * size
            val endIndex = minOf(startIndex + size, mockPayments.size)
            val pagePayments = if (startIndex < mockPayments.size) {
                mockPayments.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            mapOf(
                "payments" to pagePayments,
                "page" to page,
                "size" to size,
                "totalElements" to mockPayments.size,
                "totalPages" to (mockPayments.size + size - 1) / size,
                "hasNext" to (endIndex < mockPayments.size),
                "hasPrevious" to (page > 0)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve payment history" }
            throw BusinessException(
                "Failed to retrieve payment history: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    private fun generateMockPaymentHistory(paymentType: String?): List<Map<String, Any>> {
        // Mock payment history - in real implementation this would come from blockchain
        return listOf(
            mapOf(
                "paymentId" to "pay_001",
                "fromOrganization" to "UNI001",
                "toOrganization" to "ATTESTATION_AUTHORITY",
                "amount" to 1000.0,
                "paymentType" to "STAKE",
                "status" to "COMPLETED",
                "timestamp" to LocalDate.now().minusDays(30).format(dateFormatter)
            ),
            mapOf(
                "paymentId" to "pay_002",
                "fromOrganization" to "EMP001",
                "toOrganization" to "UNI001",
                "amount" to 5.0,
                "paymentType" to "VERIFICATION_FEE",
                "status" to "COMPLETED",
                "timestamp" to LocalDate.now().minusDays(5).format(dateFormatter)
            )
        ).filter { payment ->
            paymentType == null || payment["paymentType"] == paymentType
        }
    }
}