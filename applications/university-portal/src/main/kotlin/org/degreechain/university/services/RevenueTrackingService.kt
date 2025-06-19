package org.degreechain.university.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.university.config.UniversityConfig
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class RevenueTrackingService(
    private val contractInvoker: ContractInvoker,
    private val universityConfig: UniversityConfig
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    private fun safeMapOf(vararg pairs: Pair<String, Any?>): Map<String, Any> {
        return pairs.mapNotNull { (key, value) ->
            if (value != null) key to value else null
        }.toMap()
    }

    suspend fun getRevenueOverview(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Retrieving revenue overview for university: ${universityConfig.universityCode}" }

        try {
            // Get university statistics from blockchain
            val statsJson = contractInvoker.getUniversityStatistics(universityConfig.universityCode)
            val stats: Map<String, Any> = objectMapper.readValue(statsJson)

            val totalRevenue = (stats["revenue"] as? Number)?.toDouble() ?: 0.0
            val totalDegrees = (stats["totalDegreesIssued"] as? Number)?.toLong() ?: 0L
            val averageRevenuePerDegree = if (totalDegrees > 0) totalRevenue / totalDegrees else 0.0

            // Calculate projected annual revenue based on current performance
            val currentYear = LocalDate.now().year
            val dayOfYear = LocalDate.now().dayOfYear
            val projectedAnnualRevenue = if (dayOfYear > 0) {
                totalRevenue * (365.0 / dayOfYear)
            } else {
                0.0
            }

            safeMapOf(
                "universityCode" to universityConfig.universityCode,
                "totalRevenue" to totalRevenue,
                "totalDegreesIssued" to totalDegrees,
                "averageRevenuePerDegree" to averageRevenuePerDegree,
                "projectedAnnualRevenue" to projectedAnnualRevenue,
                "currentYear" to currentYear,
                "lastUpdated" to LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "revenueGrowthRate" to calculateRevenueGrowthRate(totalRevenue),
                "performanceMetrics" to getPerformanceMetrics(stats)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve revenue overview" }
            throw BusinessException(
                "Failed to retrieve revenue overview: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getMonthlyRevenue(year: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving monthly revenue for year: $year" }

        try {
            // In a real implementation, this would query detailed transaction history
            // For now, we'll generate mock monthly data based on total revenue
            val totalRevenue = getTotalRevenue()
            val monthlyData = generateMonthlyRevenueData(totalRevenue, year)

            safeMapOf(
                "year" to year,
                "monthlyRevenue" to monthlyData,
                "totalYearRevenue" to monthlyData.sumOf { it["revenue"] as Double },
                "averageMonthlyRevenue" to monthlyData.map { it["revenue"] as Double }.average(),
                "highestMonth" to monthlyData.maxByOrNull { it["revenue"] as Double },
                "lowestMonth" to monthlyData.minByOrNull { it["revenue"] as Double }
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve monthly revenue for year: $year" }
            throw BusinessException(
                "Failed to retrieve monthly revenue: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getVerificationTrends(days: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving verification trends for last $days days" }

        try {
            // In a real implementation, this would analyze verification patterns
            // For now, generate mock trend data
            val trendData = generateVerificationTrends(days)

            safeMapOf(
                "periodDays" to days,
                "trends" to trendData,
                "totalVerifications" to trendData.sumOf { it["verifications"] as Int },
                "averageDaily" to trendData.map { it["verifications"] as Int }.average(),
                "trendDirection" to analyzeTrendDirection(trendData),
                "peakDay" to trendData.maxByOrNull { it["verifications"] as Int }
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve verification trends" }
            throw BusinessException(
                "Failed to retrieve verification trends: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getRevenueByDegreeType(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving revenue breakdown by degree type" }

        try {
            // In a real implementation, this would aggregate revenue by degree programs
            // For now, generate mock data based on common degree types
            val degreeTypeRevenue = generateDegreeTypeRevenue()

            safeMapOf(
                "revenueByDegreeType" to degreeTypeRevenue,
                "totalRevenue" to degreeTypeRevenue.values.sum(),
                "topPerformingDegree" to degreeTypeRevenue.maxByOrNull { it.value }?.key,
                "diversityIndex" to calculateRevenueDiversityIndex(degreeTypeRevenue)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve revenue by degree type" }
            throw BusinessException(
                "Failed to retrieve revenue by degree type: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    suspend fun getRevenueComparison(
        compareWithUniversities: List<String>
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving revenue comparison with: $compareWithUniversities" }

        try {
            val comparisons = mutableListOf<Map<String, Any>>()

            // Add current university
            val currentStats = contractInvoker.getUniversityStatistics(universityConfig.universityCode)
            val currentData: Map<String, Any> = objectMapper.readValue(currentStats)

            comparisons.add(
                safeMapOf(
                    "universityCode" to universityConfig.universityCode,
                    "universityName" to currentData["universityName"],
                    "revenue" to currentData["revenue"],
                    "totalDegreesIssued" to currentData["totalDegreesIssued"],
                    "averageRevenuePerDegree" to calculateAverageRevenue(currentData),
                    "isCurrent" to true
                )
            )

            // Add comparison universities
            compareWithUniversities.forEach { universityCode ->
                try {
                    val statsJson = contractInvoker.getUniversityStatistics(universityCode)
                    val stats: Map<String, Any> = objectMapper.readValue(statsJson)

                    comparisons.add(safeMapOf(
                        "universityCode" to universityCode,
                        "universityName" to stats["universityName"],
                        "revenue" to stats["revenue"],
                        "totalDegreesIssued" to stats["totalDegreesIssued"],
                        "averageRevenuePerDegree" to calculateAverageRevenue(stats),
                        "isCurrent" to false
                    ))
                } catch (e: Exception) {
                    logger.warn { "Failed to get stats for university: $universityCode" }
                }
            }

            val currentRevenue = (currentData["revenue"] as? Number)?.toDouble() ?: 0.0
            val ranking = comparisons.sortedByDescending {
                (it["revenue"] as? Number)?.toDouble() ?: 0.0
            }.indexOfFirst { it["universityCode"] == universityConfig.universityCode } + 1

            safeMapOf(
                "comparisons" to comparisons,
                "currentUniversityRanking" to ranking,
                "totalUniversitiesCompared" to comparisons.size,
                "averageRevenue" to comparisons.mapNotNull {
                    (it["revenue"] as? Number)?.toDouble()
                }.average(),
                "performancePercentile" to calculatePercentile(currentRevenue, comparisons)
            )
        } catch (e: Exception) {
            logger.error(e) { "Failed to retrieve revenue comparison" }
            throw BusinessException(
                "Failed to retrieve revenue comparison: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    private suspend fun getTotalRevenue(): Double {
        return try {
            val statsJson = contractInvoker.getUniversityStatistics(universityConfig.universityCode)
            val stats: Map<String, Any> = objectMapper.readValue(statsJson)
            (stats["revenue"] as? Number)?.toDouble() ?: 0.0
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get total revenue, returning 0" }
            0.0
        }
    }

    private fun generateMonthlyRevenueData(totalRevenue: Double, year: Int): List<Map<String, Any>> {
        val months = listOf(
            "January", "February", "March", "April", "May", "June",
            "July", "August", "September", "October", "November", "December"
        )

        // Simulate seasonal variations in degree verifications
        val seasonalMultipliers = listOf(
            0.6, 0.5, 0.8, 1.2, 1.5, 1.8, // Jan-Jun (graduation season peaks)
            1.0, 0.7, 1.3, 1.4, 1.1, 0.9  // Jul-Dec
        )

        val monthlyBase = totalRevenue / 12

        return months.mapIndexed { index, month ->
            val revenue = monthlyBase * seasonalMultipliers[index]
            safeMapOf(
                "month" to month,
                "monthNumber" to (index + 1),
                "revenue" to revenue,
                "year" to year
            )
        }
    }

    private fun generateVerificationTrends(days: Int): List<Map<String, Any>> {
        val trends = mutableListOf<Map<String, Any>>()
        val baseVerifications = 50

        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(days - 1 - i.toLong())
            // Simulate weekday vs weekend patterns
            val dayOfWeek = date.dayOfWeek.value
            val weekendMultiplier = if (dayOfWeek in 6..7) 0.3 else 1.0

            // Add some randomness
            val randomFactor = 0.7 + (Math.random() * 0.6) // 0.7 to 1.3
            val verifications = (baseVerifications * weekendMultiplier * randomFactor).toInt()

            trends.add(safeMapOf(
                "date" to date.format(dateFormatter),
                "verifications" to verifications,
                "dayOfWeek" to date.dayOfWeek.name
            ))
        }

        return trends
    }

    private fun analyzeTrendDirection(trendData: List<Map<String, Any>>): String {
        if (trendData.size < 2) return "INSUFFICIENT_DATA"

        val firstHalf = trendData.take(trendData.size / 2)
        val secondHalf = trendData.drop(trendData.size / 2)

        val firstHalfAvg = firstHalf.map { it["verifications"] as Int }.average()
        val secondHalfAvg = secondHalf.map { it["verifications"] as Int }.average()

        return when {
            secondHalfAvg > firstHalfAvg * 1.1 -> "INCREASING"
            secondHalfAvg < firstHalfAvg * 0.9 -> "DECREASING"
            else -> "STABLE"
        }
    }

    private fun generateDegreeTypeRevenue(): Map<String, Double> {
        // Mock revenue distribution by degree type
        return safeMapOf(
            "Bachelor of Science" to 15000.0,
            "Bachelor of Arts" to 12000.0,
            "Master of Science" to 8000.0,
            "Master of Business Administration" to 10000.0,
            "Doctor of Philosophy" to 5000.0,
            "Bachelor of Engineering" to 13000.0,
            "Master of Arts" to 6000.0,
            "Bachelor of Medicine" to 18000.0
        )
    }

    private fun calculateRevenueDiversityIndex(revenueByType: Map<String, Double>): Double {
        val totalRevenue = revenueByType.values.sum()
        if (totalRevenue == 0.0) return 0.0

        val proportions = revenueByType.values.map { it / totalRevenue }
        val shannonIndex = -proportions.sumOf { p ->
            if (p > 0) p * kotlin.math.ln(p) else 0.0
        }

        // Normalize to 0-1 scale
        val maxDiversity = kotlin.math.ln(revenueByType.size.toDouble())
        return if (maxDiversity > 0) shannonIndex / maxDiversity else 0.0
    }

    private fun calculateRevenueGrowthRate(currentRevenue: Double): Double {
        // Mock calculation - in real implementation would compare with historical data
        return 0.15 // 15% growth rate
    }

    private fun getPerformanceMetrics(stats: Map<String, Any>): Map<String, Any> {
        val revenue = (stats["revenue"] as? Number)?.toDouble() ?: 0.0
        val degrees = (stats["totalDegreesIssued"] as? Number)?.toLong() ?: 0L

        return safeMapOf(
            "revenueEfficiency" to if (degrees > 0) revenue / degrees else 0.0,
            "marketShare" to 0.05, // Mock 5% market share
            "customerSatisfaction" to 4.2, // Mock rating out of 5
            "verificationAccuracy" to 0.98 // Mock 98% accuracy
        )
    }

    private fun calculateAverageRevenue(stats: Map<String, Any>): Double {
        val revenue = (stats["revenue"] as? Number)?.toDouble() ?: 0.0
        val degrees = (stats["totalDegreesIssued"] as? Number)?.toLong() ?: 0L
        return if (degrees > 0) revenue / degrees else 0.0
    }

    private fun calculatePercentile(currentRevenue: Double, comparisons: List<Map<String, Any>>): Double {
        val revenues = comparisons.mapNotNull { (it["revenue"] as? Number)?.toDouble() }.sorted()
        val position = revenues.indexOfFirst { it >= currentRevenue }
        return if (revenues.isNotEmpty() && position >= 0) {
            (position.toDouble() / revenues.size) * 100
        } else {
            0.0
        }
    }
}