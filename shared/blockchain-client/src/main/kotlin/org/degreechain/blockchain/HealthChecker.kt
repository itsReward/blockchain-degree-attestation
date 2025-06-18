package org.degreechain.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

class HealthChecker(
    private val contractInvoker: ContractInvoker
) {

    data class HealthStatus(
        val isHealthy: Boolean,
        val lastCheckTime: LocalDateTime,
        val responseTime: Long?,
        val error: String? = null
    )

    suspend fun checkHealth(timeoutMs: Long = 5000): HealthStatus = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val checkTime = LocalDateTime.now()

        try {
            val result = withTimeoutOrNull(timeoutMs) {
                contractInvoker.ping()
            }

            val responseTime = System.currentTimeMillis() - startTime

            when (result) {
                true -> {
                    logger.debug { "Blockchain health check passed in ${responseTime}ms" }
                    HealthStatus(
                        isHealthy = true,
                        lastCheckTime = checkTime,
                        responseTime = responseTime
                    )
                }
                false -> {
                    logger.warn { "Blockchain health check failed" }
                    HealthStatus(
                        isHealthy = false,
                        lastCheckTime = checkTime,
                        responseTime = responseTime,
                        error = "Ping returned false"
                    )
                }
                null -> {
                    logger.warn { "Blockchain health check timed out after ${timeoutMs}ms" }
                    HealthStatus(
                        isHealthy = false,
                        lastCheckTime = checkTime,
                        responseTime = null,
                        error = "Health check timed out"
                    )
                }
            }
        } catch (e: Exception) {
            val responseTime = System.currentTimeMillis() - startTime
            logger.error(e) { "Blockchain health check failed with exception" }
            HealthStatus(
                isHealthy = false,
                lastCheckTime = checkTime,
                responseTime = responseTime,
                error = e.message ?: "Unknown error"
            )
        }
    }

    suspend fun checkDetailedHealth(): Map<String, Any> = withContext(Dispatchers.IO) {
        val healthStatus = checkHealth()

        val details = mutableMapOf<String, Any>(
            "isHealthy" to healthStatus.isHealthy,
            "lastCheckTime" to healthStatus.lastCheckTime,
            "responseTime" to (healthStatus.responseTime ?: -1)
        )

        if (healthStatus.error != null) {
            details["error"] = healthStatus.error
        }

        // Try to get additional network information
        try {
            contractInvoker.getAllUniversities()
            details["canQueryLedger"] = true
        } catch (e: Exception) {
            details["canQueryLedger"] = false
            details["queryError"] = e.message ?: "Unknown query error"
        }

        details
    }
}