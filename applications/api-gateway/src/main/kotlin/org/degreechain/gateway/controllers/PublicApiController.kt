package org.degreechain.gateway.controllers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.gateway.services.AuthenticationService
import org.degreechain.gateway.services.RateLimitingService
import org.degreechain.gateway.services.RoutingService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1")
class PublicApiController(
    private val authenticationService: AuthenticationService,
    private val rateLimitingService: RateLimitingService,
    private val routingService: RoutingService
) {

    @PostMapping("/auth/login")
    suspend fun login(@RequestBody request: Map<String, String>): ResponseEntity<ApiResponse<Any>> {
        return try {
            val authRequest = org.degreechain.gateway.services.AuthenticationRequest(
                username = request["username"] ?: throw IllegalArgumentException("Username is required"),
                password = request["password"] ?: throw IllegalArgumentException("Password is required"),
                organizationCode = request["organizationCode"]
            )

            val result = authenticationService.authenticate(authRequest)

            if (result.success) {
                ResponseEntity.ok(ApiResponse.success(
                    data = mapOf(
                        "accessToken" to result.accessToken,
                        "refreshToken" to result.refreshToken,
                        "user" to result.user,
                        "expiresIn" to result.expiresIn
                    ),
                    message = result.message
                ))
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error<Any>(result.message))
            }
        } catch (e: Exception) {
            logger.error(e) { "Login error" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error<Any>("Login failed: ${e.message}"))
        }
    }

    @PostMapping("/auth/refresh")
    suspend fun refreshToken(@RequestBody request: Map<String, String>): ResponseEntity<ApiResponse<Any>> {
        return try {
            val refreshToken = request["refreshToken"]
                ?: throw IllegalArgumentException("Refresh token is required")

            val result = authenticationService.refreshToken(refreshToken)

            if (result.success) {
                ResponseEntity.ok(ApiResponse.success(
                    data = mapOf(
                        "accessToken" to result.accessToken,
                        "refreshToken" to result.refreshToken,
                        "user" to result.user,
                        "expiresIn" to result.expiresIn
                    ),
                    message = result.message
                ))
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error<Any>(result.message))
            }
        } catch (e: Exception) {
            logger.error(e) { "Token refresh error" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error<Any>("Token refresh failed: ${e.message}"))
        }
    }

    @PostMapping("/auth/logout")
    suspend fun logout(
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val token = extractTokenFromHeader(authorization)
            val success = authenticationService.logout(token)

            if (success) {
                ResponseEntity.ok(ApiResponse.success("Logged out successfully"))
            } else {
                ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error("Logout failed"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Logout error" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error<String>("Logout failed: ${e.message}"))
        }
    }

    @GetMapping("/health")
    suspend fun health(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val healthStatus = routingService.getAllServicesHealth()
            ResponseEntity.ok(ApiResponse.success(healthStatus, "Gateway health check"))
        } catch (e: Exception) {
            logger.error(e) { "Health check error" }
            ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error("Health check failed: ${e.message}"))
        }
    }

    @GetMapping("/admin/statistics")
    suspend fun getStatistics(
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            // Validate admin access
            val user = validateAdminAccess(authorization)

            val authStats = authenticationService.getAuthenticationStatistics()
            val rateLimitStats = rateLimitingService.getAllClientStatistics()
            val routingStats = routingService.getRoutingStatistics()

            val combinedStats = mapOf(
                "authentication" to authStats,
                "rateLimiting" to rateLimitStats,
                "routing" to routingStats,
                "gateway" to mapOf(
                    "version" to "1.0.0",
                    "uptime" to getUptimeInfo()
                )
            )

            ResponseEntity.ok(ApiResponse.success(combinedStats, "Gateway statistics"))
        } catch (e: Exception) {
            logger.error(e) { "Statistics error" }
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied or error retrieving statistics"))
        }
    }

    @RequestMapping("/**")
    suspend fun routeRequest(
        @RequestHeader headers: HttpHeaders,
        @RequestBody(required = false) body: Any?,
        request: HttpServletRequest
    ): ResponseEntity<String> {

        val method = HttpMethod.valueOf(request.method)
        val path = request.requestURI
        val clientId = getClientId(request, headers)

        logger.info { "Gateway routing: $method $path from client: $clientId" }

        return try {
            // Extract user info from token if present
            val user = extractUserFromHeaders(headers)

            // Check rate limiting
            val rateLimitResult = rateLimitingService.checkRateLimit(
                clientId = clientId,
                endpoint = path,
                userRole = user?.role
            )

            if (!rateLimitResult.allowed) {
                logger.warn { "Rate limit exceeded for client: $clientId on $path" }
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .responseHeaders.set("X-RateLimit-Limit", rateLimitResult.currentRequests?.toString() ?: "0")
                    .responseHeaders.set("X-RateLimit-Remaining", rateLimitResult.remainingRequests.toString())
                    .responseHeaders.set("X-RateLimit-Reset", rateLimitResult.resetTime?.toString() ?: "")
            }

            // Add rate limit headers to response
            val responseHeaders = HttpHeaders()
            responseHeaders.set("X-RateLimit-Limit", rateLimitResult.currentRequests.toString())
            responseHeaders.set("X-RateLimit-Remaining", rateLimitResult.remainingRequests.toString())
            responseHeaders.set("X-RateLimit-Reset", rateLimitResult.resetTime.toString())

            // Check authentication for protected endpoints
            if (requiresAuthentication(path)) {
                if (user == null) {
                    logger.warn { "Unauthenticated access attempt to protected endpoint: $path" }
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Authentication required")
                }

                // Check authorization
                if (!hasPermission(user, path, method)) {
                    logger.warn { "Unauthorized access attempt by user ${user.username} to $path" }
                    return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("Insufficient permissions")
                }
            }

            // Route the request
            val response = routingService.routeRequest(path, method, headers, body)

            // Add custom headers to response
            val finalHeaders = HttpHeaders()
            finalHeaders.addAll(response.headers)
            finalHeaders.addAll(responseHeaders)

            ResponseEntity.status(response.statusCode)
                .headers(finalHeaders)
                .body(response.body)

        } catch (e: Exception) {
            logger.error(e) { "Error routing request: $method $path" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Gateway error: ${e.message}")
        }
    }

    @PostMapping("/admin/rate-limit/reset/{clientId}")
    suspend fun resetRateLimit(
        @PathVariable clientId: String,
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            validateAdminAccess(authorization)

            val success = rateLimitingService.resetClientLimit(clientId)
            if (success) {
                ResponseEntity.ok(ApiResponse.success("Rate limit reset for client: $clientId"))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Client not found: $clientId"))
            }
        } catch (e: Exception) {
            logger.error(e) { "Rate limit reset error" }
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied or error resetting rate limit"))
        }
    }

    @PostMapping("/admin/users/{userId}/lock")
    suspend fun lockUser(
        @PathVariable userId: String,
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            validateAdminAccess(authorization)

            val success = authenticationService.lockUser(userId)
            if (success) {
                ResponseEntity.ok(ApiResponse.success("User locked successfully: $userId"))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found: $userId"))
            }
        } catch (e: Exception) {
            logger.error(e) { "User lock error" }
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied or error locking user"))
        }
    }

    private suspend fun validateAdminAccess(
        authorization: String?
    ): org.degreechain.gateway.services.UserInfo {
        val token = extractTokenFromHeader(authorization)
        val user = authenticationService.validateToken(token)
            ?: throw SecurityException("Invalid or expired token")

        if (user.role != "ADMIN" && user.role != "ATTESTATION_AUTHORITY") {
            throw SecurityException("Admin access required")
        }

        return user
    }

    private suspend fun extractUserFromHeaders(headers: HttpHeaders): org.degreechain.gateway.services.UserInfo? {
        return try {
            val authorization = headers.getFirst("Authorization")
            if (authorization != null) {
                val token = extractTokenFromHeader(authorization)
                authenticationService.validateToken(token)
            } else {
                null
            }
        } catch (e: Exception) {
            logger.debug { "Token validation failed: ${e.message}" }
            null
        }
    }

    private fun extractTokenFromHeader(authorization: String?): String {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw IllegalArgumentException("Invalid authorization header")
        }
        return authorization.substring(7)
    }

    private fun getClientId(request: HttpServletRequest, headers: HttpHeaders): String {
        // Try to get client ID from various sources
        headers.getFirst("X-Client-ID")?.let { return it }
        request.getHeader("X-Forwarded-For")?.let { return it.split(",")[0].trim() }
        request.remoteAddr?.let { return it }
        return "unknown"
    }

    private fun requiresAuthentication(path: String): Boolean {
        val publicEndpoints = listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/health",
            "/api/v1/verification/public" // If you have public verification endpoints
        )

        return !publicEndpoints.any { path.startsWith(it) }
    }

    private fun hasPermission(user: org.degreechain.gateway.services.UserInfo, path: String, method: HttpMethod): Boolean {
        // Admin has access to everything
        if (user.role == "ADMIN") {
            return true
        }

        return when {
            // Attestation Authority permissions
            user.role == "ATTESTATION_AUTHORITY" -> {
                path.startsWith("/api/v1/universities") ||
                        path.startsWith("/api/v1/governance") ||
                        path.startsWith("/api/v1/revenue") ||
                        path.startsWith("/api/v1/compliance")
            }

            // University permissions
            user.role == "UNIVERSITY" -> {
                path.startsWith("/api/v1/degrees") ||
                        path.startsWith("/api/v1/students") ||
                        (path.startsWith("/api/v1/revenue") && method == HttpMethod.GET)
            }

            // Employer permissions
            user.role == "EMPLOYER" -> {
                path.startsWith("/api/v1/verification") ||
                        path.startsWith("/api/v1/payments") ||
                        path.startsWith("/api/v1/audit")
            }

            else -> false
        }
    }

    private fun getUptimeInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val mb = 1024 * 1024

        return mapOf(
            "uptimeMs" to System.currentTimeMillis(), // Simplified - would track actual uptime
            "jvmVersion" to System.getProperty("java.version"),
            "memory" to mapOf(
                "totalMB" to runtime.totalMemory() / mb,
                "freeMB" to runtime.freeMemory() / mb,
                "maxMB" to runtime.maxMemory() / mb
            ),
            "processors" to runtime.availableProcessors()
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<String>> {
        logger.error(e) { "Unhandled gateway exception" }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiResponse.error("Gateway error: ${e.message}"))
    }

    private fun extractTokenFromHeader(authorization: String?): String {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw IllegalArgumentException("Invalid authorization header")
        }
        return authorization.substring(7)
    }

    private suspend fun validateAdminAccess(authorization: String?): UserInfo {
        val token = extractTokenFromHeader(authorization)
        val user = authenticationService.validateToken(token)
            ?: throw BusinessException("Invalid or expired token", ErrorCode.UNAUTHORIZED)

        if (user.role != "ADMIN") {
            throw BusinessException("Admin access required", ErrorCode.FORBIDDEN)
        }

        return user
    }

    private suspend fun extractUserFromHeaders(headers: HttpHeaders): UserInfo? {
        val authorization = headers.getFirst("Authorization") ?: return null
        return try {
            val token = extractTokenFromHeader(authorization)
            authenticationService.validateToken(token)
        } catch (e: Exception) {
            logger.debug { "Failed to extract user from headers: ${e.message}" }
            null
        }
    }

    private fun getClientId(request: HttpServletRequest, headers: HttpHeaders): String {
        // Try to get client ID from header first
        headers.getFirst("X-Client-Id")?.let { return it }

        // Fall back to IP address
        val xForwardedFor = headers.getFirst("X-Forwarded-For")
        return if (!xForwardedFor.isNullOrEmpty()) {
            xForwardedFor.split(",")[0].trim()
        } else {
            request.remoteAddr
        }
    }

    private fun requiresAuthentication(path: String): Boolean {
        val publicPaths = listOf(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/health",
            "/api/v1/public"
        )

        return publicPaths.none { path.startsWith(it) }
    }

    private fun hasPermission(user: UserInfo, path: String, method: HttpMethod): Boolean {
        // Admin has access to everything
        if (user.role == "ADMIN") return true

        // Check specific permissions based on path and method
        return when {
            path.startsWith("/api/v1/admin") -> false // Only admins
            path.startsWith("/api/v1/universities") && method != HttpMethod.GET -> user.role == "UNIVERSITY"
            path.startsWith("/api/v1/degrees") -> user.role in listOf("UNIVERSITY", "STUDENT")
            path.startsWith("/api/v1/verification") -> user.role == "EMPLOYER"
            else -> true // Allow for other paths
        }
    }

    private fun getUptimeInfo(): Map<String, Any> {
        val runtime = Runtime.getRuntime()
        val uptime = System.currentTimeMillis() // In production, track actual start time

        return mapOf(
            "startTime" to LocalDateTime.now().minusHours(24).toString(), // Mock data
            "uptime" to "24 hours", // Mock data
            "memory" to mapOf(
                "used" to "${runtime.totalMemory() - runtime.freeMemory()} bytes",
                "total" to "${runtime.totalMemory()} bytes",
                "max" to "${runtime.maxMemory()} bytes"
            )
        )
    }
}