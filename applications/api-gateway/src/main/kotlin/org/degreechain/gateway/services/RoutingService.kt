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
        body: Any?
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        routeRequest(attestationAuthorityUrl, path, method, headers, body)
    }

    suspend fun routeToUniversityPortal(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any?
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        routeRequest(universityPortalUrl, path, method, headers, body)
    }

    suspend fun routeToEmployerPortal(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any?
    ): ResponseEntity<String> = withContext(Dispatchers.IO) {
        routeRequest(employerPortalUrl, path, method, headers, body)
    }

    suspend fun routeRequest(
        baseUrl: String,
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: Any?
    ): ResponseEntity<String> {
        return try {
            val url = "$baseUrl$path"
            val entity = HttpEntity(body, headers)

            logger.debug { "Routing $method request to: $url" }

            val response = restTemplate.exchange(
                URI.create(url),
                method,
                entity,
                String::class.java
            )

            logger.debug { "Received response with status: ${response.statusCode}" }
            response

        } catch (e: HttpClientErrorException) {
            logger.debug { "Client error response: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.responseHeaders, e.statusCode)

        } catch (e: HttpServerErrorException) {
            logger.error { "Server error response: ${e.statusCode} - ${e.responseBodyAsString}" }
            ResponseEntity(e.responseBodyAsString, e.responseHeaders, e.statusCode)

        } catch (e: Exception) {
            logger.error(e) { "Error routing request to: $baseUrl$path" }
            throw BusinessException(
                "Failed to route request: ${e.message}",
                ErrorCode.INTERNAL_SERVER_ERROR,
                cause = e
            )
        }
    }

    fun buildHealthResponse(): Map<String, Any> {
        return mapOf(
            "status" to "UP",
            "services" to mapOf(
                "attestation-authority" to attestationAuthorityUrl,
                "university-portal" to universityPortalUrl,
                "employer-portal" to employerPortalUrl
            ),
            "timestamp" to System.currentTimeMillis()
        )
    }

    fun getAllServicesHealth(){

    }

    fun getRoutingStatistics(){

    }
}
