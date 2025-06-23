// shared/blockchain-client/src/main/kotlin/org/degreechain/blockchain/HealthChecker.kt
package org.degreechain.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

open class HealthChecker(
    protected val contractInvoker: ContractInvoker
) {
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    open suspend fun checkHealth(): HealthStatus = withContext(Dispatchers.IO) {
        val healthChecks = mutableMapOf<String, Boolean>()
        val errors = mutableListOf<String>()
        val metrics = mutableMapOf<String, Any>()

        try {
            // Test basic connectivity
            val startTime = System.currentTimeMillis()
            val pingSuccess = contractInvoker.ping()
            val responseTime = System.currentTimeMillis() - startTime

            healthChecks["blockchain_connectivity"] = pingSuccess
            metrics["response_time_ms"] = responseTime

            if (!pingSuccess) {
                errors.add("Blockchain ping failed")
            }

            // Test contract functionality
            try {
                val systemStats = contractInvoker.getSystemStatistics()
                healthChecks["system_statistics"] = true
                metrics["system_statistics_available"] = true
            } catch (e: Exception) {
                healthChecks["system_statistics"] = false
                errors.add("System statistics unavailable: ${e.message}")
                logger.warn(e) { "System statistics check failed" }
            }

            // Test university operations
            try {
                contractInvoker.getAllUniversities()
                healthChecks["university_operations"] = true
            } catch (e: Exception) {
                healthChecks["university_operations"] = false
                errors.add("University operations failed: ${e.message}")
                logger.warn(e) { "University operations check failed" }
            }

            // Overall health status
            val isHealthy = healthChecks.values.all { it }

            HealthStatus(
                isHealthy = isHealthy,
                timestamp = LocalDateTime.now().format(dateFormatter),
                checks = healthChecks,
                errors = errors,
                metrics = metrics
            )

        } catch (e: Exception) {
            logger.error(e) { "Health check failed with exception" }

            HealthStatus(
                isHealthy = false,
                timestamp = LocalDateTime.now().format(dateFormatter),
                checks = mapOf("blockchain_connectivity" to false),
                errors = listOf("Health check failed: ${e.message}"),
                metrics = emptyMap()
            )
        }
    }

    open suspend fun checkContractMethod(methodName: String, vararg args: String): Boolean = withContext(Dispatchers.IO) {
        try {
            when (methodName) {
                "getAllUniversities" -> {
                    contractInvoker.getAllUniversities()
                    true
                }
                "getSystemStatistics" -> {
                    contractInvoker.getSystemStatistics()
                    true
                }
                "getAllDegrees" -> {
                    contractInvoker.getAllDegrees()
                    true
                }
                else -> {
                    logger.warn { "Unknown health check method: $methodName" }
                    false
                }
            }
        } catch (e: Exception) {
            logger.debug(e) { "Health check for $methodName failed" }
            false
        }
    }

    open suspend fun getDetailedStatus(): DetailedHealthStatus = withContext(Dispatchers.IO) {
        val basicHealth = checkHealth()

        // Additional detailed checks
        val networkInfo = getNetworkInfo()
        val contractInfo = getContractInfo()
        val performanceMetrics = getPerformanceMetrics()

        DetailedHealthStatus(
            basicHealth = basicHealth,
            networkInfo = networkInfo,
            contractInfo = contractInfo,
            performanceMetrics = performanceMetrics
        )
    }

    private suspend fun getNetworkInfo(): NetworkInfo = withContext(Dispatchers.IO) {
        try {
            // In a real implementation, this would query actual network information
            NetworkInfo(
                channelName = "degree-channel", // From config
                chaincodeVersion = "1.0.0",
                blockHeight = null, // Would need fabric-sdk-java for this
                connectedPeers = 1,
                isConnected = true
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get network info" }
            NetworkInfo(
                channelName = "unknown",
                chaincodeVersion = "unknown",
                blockHeight = null,
                connectedPeers = 0,
                isConnected = false
            )
        }
    }

    private suspend fun getContractInfo(): ContractInfo = withContext(Dispatchers.IO) {
        try {
            val systemStats = contractInvoker.getSystemStatistics()
            ContractInfo(
                contractName = "DegreeAttestationContract",
                isDeployed = true,
                lastInteraction = LocalDateTime.now().format(dateFormatter),
                totalTransactions = null // Would need to parse from system stats
            )
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get contract info" }
            ContractInfo(
                contractName = "DegreeAttestationContract",
                isDeployed = false,
                lastInteraction = null,
                totalTransactions = null
            )
        }
    }

    private suspend fun getPerformanceMetrics(): PerformanceMetrics = withContext(Dispatchers.IO) {
        val measurements = mutableListOf<Long>()

        // Take 5 ping measurements
        repeat(5) {
            try {
                val startTime = System.currentTimeMillis()
                contractInvoker.ping()
                val responseTime = System.currentTimeMillis() - startTime
                measurements.add(responseTime)
            } catch (e: Exception) {
                // Skip failed measurements
            }
        }

        PerformanceMetrics(
            averageResponseTime = if (measurements.isNotEmpty()) measurements.average() else 0.0,
            minResponseTime = measurements.minOrNull() ?: 0,
            maxResponseTime = measurements.maxOrNull() ?: 0,
            successfulRequests = measurements.size,
            failedRequests = 5 - measurements.size,
            uptime = null // Would need to track startup time
        )
    }
}

data class HealthStatus(
    val isHealthy: Boolean,
    val timestamp: String,
    val checks: Map<String, Boolean>,
    val errors: List<String>,
    val metrics: Map<String, Any>
)

data class DetailedHealthStatus(
    val basicHealth: HealthStatus,
    val networkInfo: NetworkInfo,
    val contractInfo: ContractInfo,
    val performanceMetrics: PerformanceMetrics
)

data class NetworkInfo(
    val channelName: String,
    val chaincodeVersion: String,
    val blockHeight: Long?,
    val connectedPeers: Int,
    val isConnected: Boolean
)

data class ContractInfo(
    val contractName: String,
    val isDeployed: Boolean,
    val lastInteraction: String?,
    val totalTransactions: Long?
)

data class PerformanceMetrics(
    val averageResponseTime: Double,
    val minResponseTime: Long,
    val maxResponseTime: Long,
    val successfulRequests: Int,
    val failedRequests: Int,
    val uptime: Long?
)