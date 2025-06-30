package org.degreechain.gateway.services

import org.degreechain.gateway.config.VeryPhyConfig
import org.degreechain.gateway.models.*
import org.springframework.core.io.FileSystemResource
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import sun.jvm.hotspot.HelloWorld.e
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class VeryPhyApiClient(
    private val config: VeryPhyConfig,
    private val restTemplate: RestTemplate
) {
    private val logger = LoggerFactory.getLogger(VeryPhyApiClient::class.java)

    /**
     * Process certificate with VeryPhy API - embeds hash and returns processed certificate
     */
    fun processCertificate(
        file: MultipartFile,
        embedHash: Boolean = true,
        addWatermark: Boolean = false,
        watermarkText: String = "VERIFIED"
    ): ProcessedCertificateResponse {
        logger.info("Processing certificate with VeryPhy API: ${file.originalFilename}")

        validateFile(file)

        // Create temporary file
        val tempFile = Files.createTempFile("cert_", "_${file.originalFilename}")
        try {
            Files.copy(file.inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)

            // Prepare request
            val headers = createHeaders()
            val body = createMultipartBody(tempFile, mapOf(
                "embed_hash" to embedHash.toString(),
                "add_watermark" to addWatermark.toString(),
                "watermark_text" to watermarkText
            ))

            val requestEntity = HttpEntity(body, headers)

            // Make API call with retry logic
            val response = executeWithRetry {
                restTemplate.exchange(
                    "${config.baseUrl}/api/v1/certificates/upload",
                    HttpMethod.POST,
                    requestEntity,
                    ProcessedCertificateResponse::class.java
                )
            }

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                logger.info("Certificate processed successfully with hash: ${response.body!!.hash}")
                return response.body!!
            } else {
                throw RuntimeException("Failed to process certificate: ${response.statusCode}")
            }

        } catch (e: Exception) {
            logger.error("Error processing certificate with VeryPhy API", e)
            throw VeryPhyApiException("Certificate processing failed: ${e.message}", e)
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(tempFile)
        }
    }

    /**
     * Extract certificate data and hash from uploaded certificate
     */
    fun extractCertificateData(
        file: MultipartFile,
        useEnhancedExtraction: Boolean = true,
        expectedHash: String? = null
    ): VerificationExtractionResponse {
        logger.info("Extracting certificate data with VeryPhy API: ${file.originalFilename}")

        validateFile(file)

        // Create temporary file
        val tempFile = Files.createTempFile("verify_", "_${file.originalFilename}")
        try {
            Files.copy(file.inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)

            // Prepare request
            val headers = createHeaders()
            val body = createMultipartBody(tempFile, mapOf(
                "use_enhanced_extraction" to useEnhancedExtraction.toString(),
                "check_database" to "false", // We'll handle blockchain verification
                "expected_hash" to (expectedHash ?: "")
            ))

            val requestEntity = HttpEntity(body, headers)

            // Make API call with retry logic
            val response = executeWithRetry {
                restTemplate.exchange(
                    "${config.baseUrl}/api/v1/verify/",
                    HttpMethod.POST,
                    requestEntity,
                    VerificationExtractionResponse::class.java
                )
            }

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                logger.info("Certificate data extracted successfully. Method: ${response.body!!.extractionMethod}")
                return response.body!!
            } else {
                throw RuntimeException("Failed to extract certificate data: ${response.statusCode}")
            }

        } catch (e: Exception) {
            logger.error("Error extracting certificate data with VeryPhy API", e)
            throw VeryPhyApiException("Certificate data extraction failed: ${e.message}", e)
        } finally {
            // Clean up temporary file
            Files.deleteIfExists(tempFile)
        }
    }

    /**
     * Batch process multiple certificates
     */
    fun batchProcessCertificates(
        files: List<MultipartFile>,
        embedHash: Boolean = true
    ): List<ProcessedCertificateResponse> {
        logger.info("Batch processing ${files.size} certificates")

        val results = mutableListOf<ProcessedCertificateResponse>()
        val tempFiles = mutableListOf<java.nio.file.Path>()

        try {
            // Prepare all files
            files.forEach { file ->
                validateFile(file)
                val tempFile = Files.createTempFile("batch_cert_", "_${file.originalFilename}")
                Files.copy(file.inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING)
                tempFiles.add(tempFile)
            }

            // Prepare request
            val headers = createHeaders()
            val body: MultiValueMap<String, Any> = LinkedMultiValueMap()

            tempFiles.forEach { tempFile ->
                body.add("files", FileSystemResource(tempFile))
            }
            body.add("embed_hash", embedHash.toString())

            val requestEntity = HttpEntity(body, headers)

            // Make API call
            val response = executeWithRetry {
                restTemplate.exchange(
                    "${config.baseUrl}/api/v1/verify/batch",
                    HttpMethod.POST,
                    requestEntity,
                    BatchProcessingResponse::class.java
                )
            }

            if (response.statusCode == HttpStatus.OK && response.body != null) {
                logger.info("Batch processing completed successfully")
                return response.body!!.results
            } else {
                throw RuntimeException("Batch processing failed: ${response.statusCode}")
            }

        } catch (e: Exception) {
            logger.error("Error in batch processing", e)
            throw VeryPhyApiException("Batch processing failed: ${e.message}", e)
        } finally {
            // Clean up all temporary files
            tempFiles.forEach { Files.deleteIfExists(it) }
        }
    }

    /**
     * Check VeryPhy API health status
     */
    fun checkApiHealth(): VeryPhyHealthStatus {
        return try {
            val response = restTemplate.getForEntity(
                "${config.baseUrl}/api/v1/health",
                Map::class.java
            )

            if (response.statusCode == HttpStatus.OK) {
                val body = response.body as? Map<String, Any>
                VeryPhyHealthStatus(
                    isHealthy = true,
                    status = body?.get("status")?.toString() ?: "healthy",
                    version = body?.get("version")?.toString() ?: "unknown",
                    services = body?.get("services") as? Map<String, Boolean> ?: emptyMap(),
                    responseTime = System.currentTimeMillis()
                )
            } else {
                VeryPhyHealthStatus(
                    isHealthy = false,
                    status = "unhealthy",
                    version = "unknown",
                    services = emptyMap(),
                    responseTime = System.currentTimeMillis(),
                    error = "HTTP ${response.statusCode}"
                )
            }
        } catch (e: Exception) {
            logger.warn("VeryPhy API health check failed", e)
            VeryPhyHealthStatus(
                isHealthy = false,
                status = "unreachable",
                version = "unknown",
                services = emptyMap(),
                responseTime = System.currentTimeMillis(),
                error = e.message
            )
        }
    }

    /**
     * Get API usage statistics
     */
    fun getApiStatistics(): VeryPhyApiStatistics {
        return try {
            val response = restTemplate.exchange(
                "${config.baseUrl}/api/v1/statistics",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                Map::class.java
            )

            VeryPhyApiStatistics(
                totalProcessed = (stats["total_processed"] as? Number)?.toLong() ?: 0L,
                totalVerifications = (stats["total_verifications"] as? Number)?.toLong() ?: 0L,
                averageProcessingTime = (stats["average_processing_time"] as? Number)?.toDouble() ?: 0.0,
                successRate = (stats["success_rate"] as? Number)?.toDouble() ?: 0.0,
                uptime = (stats["uptime"] as? Number)?.toLong() ?: 0L
            )
        } else {
            VeryPhyApiStatistics()
        }
    } catch (e: Exception) {
        logger.warn("Failed to fetch VeryPhy API statistics", e)
        VeryPhyApiStatistics()
    }
}

// ========== PRIVATE HELPER METHODS ==========

private fun validateFile(file: MultipartFile) {
    if (file.isEmpty) {
        throw IllegalArgumentException("File cannot be empty")
    }

    if (file.size > config.maxFileSize) {
        throw IllegalArgumentException("File size exceeds maximum allowed size: ${config.maxFileSize}")
    }

    val filename = file.originalFilename ?: ""
    val extension = filename.substringAfterLast('.', "").lowercase()
    if (!config.allowedFileTypes.contains(extension)) {
        throw IllegalArgumentException("File type not allowed. Supported types: ${config.allowedFileTypes}")
    }
}

private fun createHeaders(): HttpHeaders {
    return HttpHeaders().apply {
        contentType = MediaType.MULTIPART_FORM_DATA
        set("Authorization", "Bearer ${config.apiKey}")
        set("User-Agent", "DegreeAttestationGateway/1.0")
    }
}

private fun createMultipartBody(
    file: java.nio.file.Path,
    additionalParams: Map<String, String> = emptyMap()
): MultiValueMap<String, Any> {
    val body: MultiValueMap<String, Any> = LinkedMultiValueMap()
    body.add("file", FileSystemResource(file))

    additionalParams.forEach { (key, value) ->
        body.add(key, value)
    }

    return body
}

private fun <T> executeWithRetry(operation: () -> ResponseEntity<T>): ResponseEntity<T> {
    var lastException: Exception? = null

    repeat(config.maxRetries) { attempt ->
        try {
            return operation()
        } catch (e: HttpClientErrorException) {
            // Don't retry on client errors (4xx)
            throw VeryPhyApiException("Client error: ${e.statusCode} - ${e.responseBodyAsString}", e)
        } catch (e: HttpServerErrorException) {
            // Retry on server errors (5xx)
            lastException = e
            if (attempt < config.maxRetries - 1) {
                logger.warn("VeryPhy API call failed (attempt ${attempt + 1}/${config.maxRetries}), retrying...", e)
                Thread.sleep(config.retryDelay.toMillis())
            }
        } catch (e: Exception) {
            // Retry on network errors
            lastException = e
            if (attempt < config.maxRetries - 1) {
                logger.warn("VeryPhy API call failed (attempt ${attempt + 1}/${config.maxRetries}), retrying...", e)
                Thread.sleep(config.retryDelay.toMillis())
            }
        }
    }

    throw VeryPhyApiException("VeryPhy API call failed after ${config.maxRetries} attempts", lastException)
}
}

/**
 * Custom exception for VeryPhy API errors
 */
class VeryPhyApiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * VeryPhy API health status
 */
data class VeryPhyHealthStatus(
    val isHealthy: Boolean,
    val status: String,
    val version: String,
    val services: Map<String, Boolean>,
    val responseTime: Long,
    val error: String? = null
)

/**
 * VeryPhy API statistics
 */
data class VeryPhyApiStatistics(
    val totalProcessed: Long = 0L,
    val totalVerifications: Long = 0L,
    val averageProcessingTime: Double = 0.0,
    val successRate: Double = 0.0,
    val uptime: Long = 0L
)

/**
 * Batch processing response
 */
data class BatchProcessingResponse(
    val results: List<ProcessedCertificateResponse>,
    val totalProcessed: Int,
    val successCount: Int,
    val failureCount: Int,
    val processingTime: Long
)