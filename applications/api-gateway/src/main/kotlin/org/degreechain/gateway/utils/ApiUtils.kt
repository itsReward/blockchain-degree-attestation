package org.degreechain.gateway.utils

import org.degreechain.gateway.controllers.ApiResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Utility class for API operations and response handling
 */
object ApiUtils {
    private val logger = LoggerFactory.getLogger(ApiUtils::class.java)

    // Metrics tracking
    private val requestCounts = ConcurrentHashMap<String, AtomicLong>()
    private val responseTimes = ConcurrentHashMap<String, MutableList<Long>>()
    private val errorCounts = ConcurrentHashMap<String, AtomicLong>()

    /**
     * Create successful API response
     */
    fun <T> createSuccessResponse(data: T): ResponseEntity<ApiResponse<T>> {
        return ResponseEntity.ok(
            ApiResponse(
                success = true,
                data = data,
                message = "Operation completed successfully",
                timestamp = LocalDateTime.now()
            )
        )
    }

    /**
     * Create error API response
     */
    fun createErrorResponse(
        message: String,
        errorCode: String,
        status: HttpStatus = HttpStatus.BAD_REQUEST
    ): ResponseEntity<ApiResponse<Nothing>> {
        logger.warn("API Error - Code: $errorCode, Message: $message")

        return ResponseEntity.status(status).body(
            ApiResponse(
                success = false,
                data = null,
                message = message,
                timestamp = LocalDateTime.now(),
                error = errorCode
            )
        )
    }

    /**
     * Create validation error response
     */
    fun createValidationErrorResponse(
        validationErrors: List<String>
    ): ResponseEntity<ApiResponse<Nothing>> {
        val message = "Validation failed: ${validationErrors.joinToString(", ")}"
        return createErrorResponse(message, "VALIDATION_ERROR", HttpStatus.BAD_REQUEST)
    }

    /**
     * Create not found response
     */
    fun createNotFoundResponse(resource: String): ResponseEntity<ApiResponse<Nothing>> {
        return createErrorResponse("$resource not found", "NOT_FOUND", HttpStatus.NOT_FOUND)
    }

    /**
     * Create unauthorized response
     */
    fun createUnauthorizedResponse(): ResponseEntity<ApiResponse<Nothing>> {
        return createErrorResponse("Unauthorized access", "UNAUTHORIZED", HttpStatus.UNAUTHORIZED)
    }

    /**
     * Create forbidden response
     */
    fun createForbiddenResponse(): ResponseEntity<ApiResponse<Nothing>> {
        return createErrorResponse("Access forbidden", "FORBIDDEN", HttpStatus.FORBIDDEN)
    }

    /**
     * Create internal server error response
     */
    fun createInternalErrorResponse(message: String? = null): ResponseEntity<ApiResponse<Nothing>> {
        val errorMessage = message ?: "Internal server error occurred"
        return createErrorResponse(errorMessage, "INTERNAL_ERROR", HttpStatus.INTERNAL_SERVER_ERROR)
    }

    /**
     * Create service unavailable response
     */
    fun createServiceUnavailableResponse(service: String): ResponseEntity<ApiResponse<Nothing>> {
        return createErrorResponse(
            "$service is currently unavailable",
            "SERVICE_UNAVAILABLE",
            HttpStatus.SERVICE_UNAVAILABLE
        )
    }

    /**
     * Create rate limit exceeded response
     */
    fun createRateLimitResponse(): ResponseEntity<ApiResponse<Nothing>> {
        return createErrorResponse(
            "Rate limit exceeded. Please try again later",
            "RATE_LIMIT_EXCEEDED",
            HttpStatus.TOO_MANY_REQUESTS
        )
    }

    /**
     * Extract client IP address from request
     */
    fun extractClientIp(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        val xRealIp = request.getHeader("X-Real-IP")
        val xClientIp = request.getHeader("X-Client-IP")

        return when {
            !xForwardedFor.isNullOrBlank() -> {
                // X-Forwarded-For can contain multiple IPs, take the first one
                xForwardedFor.split(",").first().trim()
            }
            !xRealIp.isNullOrBlank() -> xRealIp.trim()
            !xClientIp.isNullOrBlank() -> xClientIp.trim()
            else -> request.remoteAddr ?: "unknown"
        }
    }

    /**
     * Extract user agent from request
     */
    fun extractUserAgent(request: HttpServletRequest): String {
        return request.getHeader("User-Agent") ?: "unknown"
    }

    /**
     * Log API call with metrics
     */
    fun logApiCall(endpoint: String, duration: Long, success: Boolean) {
        try {
            // Update request count
            requestCounts.computeIfAbsent(endpoint) { AtomicLong(0) }.incrementAndGet()

            // Update response times
            responseTimes.computeIfAbsent(endpoint) { mutableListOf() }.apply {
                synchronized(this) {
                    add(duration)
                    // Keep only last 1000 entries to prevent memory issues
                    if (size > 1000) {
                        removeAt(0)
                    }
                }
            }

            // Update error count
            if (!success) {
                errorCounts.computeIfAbsent(endpoint) { AtomicLong(0) }.incrementAndGet()
            }

            // Log based on duration and success
            when {
                !success -> logger.warn("API Call Failed - Endpoint: $endpoint, Duration: ${duration}ms")
                duration > 10000 -> logger.warn("Slow API Call - Endpoint: $endpoint, Duration: ${duration}ms")
                duration > 5000 -> logger.info("API Call - Endpoint: $endpoint, Duration: ${duration}ms (slow)")
                else -> logger.debug("API Call - Endpoint: $endpoint, Duration: ${duration}ms")
            }

        } catch (e: Exception) {
            logger.error("Error logging API call metrics", e)
        }
    }

    /**
     * Get API metrics for monitoring
     */
    fun getApiMetrics(): Map<String, Any> {
        return try {
            val metrics = mutableMapOf<String, Any>()

            // Request counts
            val totalRequests = requestCounts.values.sumOf { it.get() }
            metrics["totalRequests"] = totalRequests
            metrics["endpointCounts"] = requestCounts.mapValues { it.value.get() }

            // Error rates
            val totalErrors = errorCounts.values.sumOf { it.get() }
            metrics["totalErrors"] = totalErrors
            metrics["errorRate"] = if (totalRequests > 0) totalErrors.toDouble() / totalRequests else 0.0
            metrics["endpointErrorCounts"] = errorCounts.mapValues { it.value.get() }

            // Response times
            val allResponseTimes = responseTimes.values.flatten()
            if (allResponseTimes.isNotEmpty()) {
                metrics["averageResponseTime"] = allResponseTimes.average()
                metrics["medianResponseTime"] = allResponseTimes.sorted()[allResponseTimes.size / 2]
                metrics["maxResponseTime"] = allResponseTimes.maxOrNull() ?: 0
                metrics["minResponseTime"] = allResponseTimes.minOrNull() ?: 0
            } else {
                metrics["averageResponseTime"] = 0.0
                metrics["medianResponseTime"] = 0
                metrics["maxResponseTime"] = 0
                metrics["minResponseTime"] = 0
            }

            // Per-endpoint average response times
            metrics["endpointResponseTimes"] = responseTimes.mapValues { (_, times) ->
                if (times.isNotEmpty()) times.average() else 0.0
            }

            metrics["generatedAt"] = LocalDateTime.now().toString()
            metrics

        } catch (e: Exception) {
            logger.error("Error generating API metrics", e)
            mapOf("error" to "Failed to generate metrics")
        }
    }

    /**
     * Reset API metrics (useful for testing or periodic resets)
     */
    fun resetMetrics() {
        requestCounts.clear()
        responseTimes.clear()
        errorCounts.clear()
        logger.info("API metrics reset")
    }

    /**
     * Validate request parameters
     */
    fun validateRequiredParams(params: Map<String, String?>, requiredFields: List<String>): List<String> {
        val errors = mutableListOf<String>()

        requiredFields.forEach { field ->
            val value = params[field]
            if (value.isNullOrBlank()) {
                errors.add("$field is required")
            }
        }

        return errors
    }

    /**
     * Sanitize input string for security
     */
    fun sanitizeInput(input: String?): String {
        if (input == null) return ""

        return input
            .trim()
            .replace(Regex("[<>\"'&]"), "") // Remove potentially harmful characters
            .take(1000) // Limit length
    }

    /**
     * Check if request is from localhost/internal network
     */
    fun isInternalRequest(request: HttpServletRequest): Boolean {
        val clientIp = extractClientIp(request)
        return isInternalIp(clientIp)
    }

    /**
     * Check if IP address is internal/localhost
     */
    fun isInternalIp(ip: String): Boolean {
        return when {
            ip == "127.0.0.1" || ip == "::1" -> true // localhost
            ip.startsWith("192.168.") -> true // private network
            ip.startsWith("10.") -> true // private network
            ip.startsWith("172.") -> {
                // Check if it's in 172.16.0.0 - 172.31.255.255 range
                val parts = ip.split(".")
                if (parts.size >= 2) {
                    val secondOctet = parts[1].toIntOrNull()
                    secondOctet != null && secondOctet in 16..31
                } else false
            }
            else -> false
        }
    }

    /**
     * Format duration in human-readable format
     */
    fun formatDuration(milliseconds: Long): String {
        return when {
            milliseconds < 1000 -> "${milliseconds}ms"
            milliseconds < 60000 -> "%.1fs".format(milliseconds / 1000.0)
            else -> "${milliseconds / 60000}m ${(milliseconds % 60000) / 1000}s"
        }
    }

    /**
     * Generate correlation ID for request tracking
     */
    fun generateCorrelationId(): String {
        return "${System.currentTimeMillis()}-${(1000..9999).random()}"
    }

    /**
     * Mask sensitive data in logs
     */
    fun maskSensitiveData(data: String): String {
        return data
            .replace(Regex("(?i)(password|token|key|secret)[\"']?\\s*[:=]\\s*[\"']?([^\"',\\s}]+)"), "$1: ***")
            .replace(Regex("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b"), "**** **** **** ****") // Credit card
            .replace(Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b"), "***@***.***") // Email
    }

    /**
     * Create paginated response
     */
    fun <T> createPaginatedResponse(
        data: List<T>,
        page: Int,
        size: Int,
        total: Long
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        val totalPages = (total + size - 1) / size // Ceiling division

        val paginatedData = mapOf(
            "content" to data,
            "page" to page,
            "size" to size,
            "totalElements" to total,
            "totalPages" to totalPages,
            "first" to (page == 0),
            "last" to (page >= totalPages - 1),
            "numberOfElements" to data.size
        )

        return createSuccessResponse(paginatedData)
    }
}