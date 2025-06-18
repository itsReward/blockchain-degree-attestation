package org.degreechain.gateway.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.net.URI

private val logger = KotlinLogging.logger {}

@Service
class RoutingService(
    private val restTemplate: RestTemplate
) {

    @Value("\${services.attestation-authority.url:http://localhost:8080}")
    private lateinit var attestationAuthorityUrl: String

    @Value("\${services.university-portal.url:http://localhost:8081}")
    private lateinit var universityPortalUrl: String

    @Value("\${services.employer-portal.url:http://localhost:8082}")
    private lateinit var employerPortalUrl: String

    suspend fun routeToAttestationAuthority(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any? = null
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        logger.debug { "Routing to Attestation Authority: $method $path" }

        try {
            val url = "$attestationAuthorityUrl$path"
            val requestEntity = HttpEntity(body, headers)

            val response = when (method) {
                HttpMethod.GET -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.POST -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.PUT -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.DELETE -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                else -> throw BusinessException(
                    "Unsupported HTTP method: $method",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            logger.debug { "Successfully routed to Attestation Authority: ${response.statusCode}" }
            response

        } catch (e: HttpClientErrorException) {
            logger.warn { "Client error from Attestation Authority: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: HttpServerErrorException) {
            logger.error { "Server error from Attestation Authority: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: Exception) {
            logger.error(e) { "Error routing to Attestation Authority" }
            throw BusinessException(
                "Failed to route request to Attestation Authority: ${e.message}",
                ErrorCode.SERVICE_UNAVAILABLE,
                cause = e
            )
        }
    }

    suspend fun routeToUniversityPortal(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any? = null
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        logger.debug { "Routing to University Portal: $method $path" }

        try {
            val url = "$universityPortalUrl$path"
            val requestEntity = HttpEntity(body, headers)

            val response = when (method) {
                HttpMethod.GET -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.POST -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.PUT -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.DELETE -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                else -> throw BusinessException(
                    "Unsupported HTTP method: $method",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            logger.debug { "Successfully routed to University Portal: ${response.statusCode}" }
            response

        } catch (e: HttpClientErrorException) {
            logger.warn { "Client error from University Portal: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: HttpServerErrorException) {
            logger.error { "Server error from University Portal: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: Exception) {
            logger.error(e) { "Error routing to University Portal" }
            throw BusinessException(
                "Failed to route request to University Portal: ${e.message}",
                ErrorCode.SERVICE_UNAVAILABLE,
                cause = e
            )
        }
    }

    suspend fun routeToEmployerPortal(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any? = null
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        logger.debug { "Routing to Employer Portal: $method $path" }

        try {
            val url = "$employerPortalUrl$path"
            val requestEntity = HttpEntity(body, headers)

            val response = when (method) {
                HttpMethod.GET -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.POST -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.PUT -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                HttpMethod.DELETE -> restTemplate.exchange(url, method, requestEntity, String::class.java)
                else -> throw BusinessException(
                    "Unsupported HTTP method: $method",
                    ErrorCode.VALIDATION_ERROR
                )
            }

            logger.debug { "Successfully routed to Employer Portal: ${response.statusCode}" }
            response

        } catch (e: HttpClientErrorException) {
            logger.warn { "Client error from Employer Portal: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: HttpServerErrorException) {
            logger.error { "Server error from Employer Portal: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.statusCode)
        } catch (e: Exception) {
            logger.error(e) { "Error routing to Employer Portal" }
            throw BusinessException(
                "Failed to route request to Employer Portal: ${e.message}",
                ErrorCode.SERVICE_UNAVAILABLE,
                cause = e
            )
        }
    }

    suspend fun checkServiceHealth(serviceName: String): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Checking health of service: $serviceName" }

        val serviceUrl = when (serviceName.lowercase()) {
            "attestation-authority" -> attestationAuthorityUrl
            "university-portal" -> universityPortalUrl
            "employer-portal" -> employerPortalUrl
            else -> throw BusinessException(
                "Unknown service: $serviceName",
                ErrorCode.VALIDATION_ERROR
            )
        }

        try {
            val healthUrl = "$serviceUrl/actuator/health"
            val response = restTemplate.getForEntity(healthUrl, String::class.java)

            mapOf(
                "serviceName" to serviceName,
                "status" to "UP",
                "httpStatus" to response.statusCode.value(),
                "responseTime" to "< 1s", // Simplified
                "url" to serviceUrl
            )

        } catch (e: Exception) {
            logger.warn(e) { "Health check failed for service: $serviceName" }
            mapOf(
                "serviceName" to serviceName,
                "status" to "DOWN",
                "error" to e.message,
                "url" to serviceUrl
            )
        }
    }

    suspend fun getAllServicesHealth(): Map<String, Any> = withContext(Dispatchers.IO) {
        val services = listOf("attestation-authority", "university-portal", "employer-portal")
        val healthChecks = services.associateWith { serviceName ->
            try {
                checkServiceHealth(serviceName)
            } catch (e: Exception) {
                mapOf(
                    "serviceName" to serviceName,
                    "status" to "DOWN",
                    "error" to e.message
                )
            }
        }

        val allHealthy = healthChecks.values.all {
            (it["status"] as String) == "UP"
        }

        mapOf(
            "overallStatus" to if (allHealthy) "UP" else "DEGRADED",
            "services" to healthChecks,
            "timestamp" to System.currentTimeMillis()
        )
    }

    fun determineTargetService(path: String): String {
        return when {
            path.startsWith("/api/v1/universities") -> "attestation-authority"
            path.startsWith("/api/v1/governance") -> "attestation-authority"
            path.startsWith("/api/v1/revenue") -> "attestation-authority"
            path.startsWith("/api/v1/compliance") -> "attestation-authority"
            path.startsWith("/api/v1/degrees") -> "university-portal"
            path.startsWith("/api/v1/students") -> "university-portal"
            path.startsWith("/api/v1/verification") -> "employer-portal"
            path.startsWith("/api/v1/payments") -> "employer-portal"
            path.startsWith("/api/v1/audit") -> "employer-portal"
            else -> throw BusinessException(
                "No service mapping found for path: $path",
                ErrorCode.RESOURCE_NOT_FOUND
            )
        }
    }

    suspend fun routeRequest(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any? = null
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        val targetService = determineTargetService(path)

        logger.info { "Routing $method $path to $targetService" }

        when (targetService) {
            "attestation-authority" -> routeToAttestationAuthority(path, method, headers, body)
            "university-portal" -> routeToUniversityPortal(path, method, headers, body)
            "employer-portal" -> routeToEmployerPortal(path, method, headers, body)
            else -> throw BusinessException(
                "Unknown target service: $targetService",
                ErrorCode.INTERNAL_SERVER_ERROR
            )
        }
    }

    suspend fun getRoutingStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        // In a real implementation, this would track actual routing metrics
        // For now, return mock statistics
        mapOf(
            "totalRequests" to 15847,
            "requestsToday" to 234,
            "averageResponseTime" to 120, // milliseconds
            "errorRate" to 0.02, // 2%
            "serviceDistribution" to mapOf(
                "attestation-authority" to 4520,
                "university-portal" to 6891,
                "employer-portal" to 4436
            ),
            "topEndpoints" to listOf(
                mapOf("path" to "/api/v1/verification/verify", "count" to 3421),
                mapOf("path" to "/api/v1/degrees/submit", "count" to 2987),
                mapOf("path" to "/api/v1/universities", "count" to 1876)
            ),
            "timestamp" to System.currentTimeMillis()
        )
    }
}