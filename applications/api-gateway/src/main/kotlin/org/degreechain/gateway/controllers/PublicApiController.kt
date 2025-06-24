package org.degreechain.gateway.controllers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.gateway.services.AuthenticationService
import org.degreechain.gateway.services.RoutingService
import org.degreechain.gateway.services.UserInfo
import org.degreechain.gateway.services.AuthenticationRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import jakarta.servlet.http.HttpServletRequest
import org.degreechain.gateway.services.RateLimitingService

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1")
class PublicApiController(  // Fixed: Changed from GatewayController to PublicApiController
    private val authenticationService: AuthenticationService,
    private val rateLimitingService: RateLimitingService,
    private val routingService: RoutingService
) {

    @PostMapping("/auth/login")
    suspend fun login(@RequestBody request: Map<String, String>): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val authRequest = AuthenticationRequest(
                username = request["username"] ?: throw IllegalArgumentException("Username is required"),
                password = request["password"] ?: throw IllegalArgumentException("Password is required"),
                organizationCode = request["organizationCode"]
            )

            val result = authenticationService.authenticate(authRequest)

            if (result.success) {
                val responseData = mapOf(
                    "accessToken" to result.accessToken,
                    "refreshToken" to result.refreshToken,
                    "user" to result.user,
                    "expiresIn" to result.expiresIn
                )
                ResponseEntity.ok(ApiResponse.success(responseData, result.message))
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error<Map<String, Any>>(result.message))
            }
        } catch (e: Exception) {
            logger.error(e) { "Login error" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error<Map<String, Any>>("Login failed: ${e.message}"))
        } as ResponseEntity<ApiResponse<Map<String, Any>>>
    }

    @PostMapping("/auth/refresh")
    suspend fun refreshToken(@RequestBody request: Map<String, String>): ResponseEntity<ApiResponse<Map<String, Any>>> {  // Fixed: Changed from Any to Map<String, Any>
        return try {
            val refreshToken = request["refreshToken"]
                ?: throw IllegalArgumentException("Refresh token is required")

            val result = authenticationService.refreshToken(refreshToken)

            if (result.success) {
                val responseData = mapOf(
                    "accessToken" to result.accessToken,
                    "refreshToken" to result.refreshToken,
                    "user" to result.user,
                    "expiresIn" to result.expiresIn
                )
                ResponseEntity.ok(ApiResponse.success(responseData, result.message))
            } else {
                ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error<Map<String, Any>>(result.message))
            }
        } catch (e: Exception) {
            logger.error(e) { "Token refresh error" }
            ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error<Map<String, Any>>("Token refresh failed: ${e.message}"))
        } as ResponseEntity<ApiResponse<Map<String, Any>>>
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
                .body(ApiResponse.error<Map<String, Any>>("Access denied or error retrieving statistics"))
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
                ResponseEntity.ok(ApiResponse.success("User locked: $userId"))
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

    @PostMapping("/admin/users/{userId}/unlock")
    suspend fun unlockUser(
        @PathVariable userId: String,
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            validateAdminAccess(authorization)

            val success = authenticationService.unlockUser(userId)
            if (success) {
                ResponseEntity.ok(ApiResponse.success("User unlocked: $userId"))
            } else {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found: $userId"))
            }
        } catch (e: Exception) {
            logger.error(e) { "User unlock error" }
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error("Access denied or error unlocking user"))
        }
    }
/*
    @GetMapping("/admin/rate-limit/violations")
    suspend fun getRateLimitViolations(
        @RequestParam(defaultValue = "24") hours: Int,
        @RequestHeader("Authorization") authorization: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            validateAdminAccess(authorization)

            val violations = rateLimitingService.getViolationReport(hours)
            ResponseEntity.ok(ApiResponse.success(violations, "Rate limit violations report"))
        } catch (e: Exception) {
            logger.error(e) { "Violations report error" }
            ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error<Map<String, Any>>("Access denied or error retrieving violations"))
        }
    }
    */

    @RequestMapping("/**")
    suspend fun routeRequest(
        @RequestHeader headers: HttpHeaders,
        @RequestBody(required = false) body: String?,  // Fixed: Changed from Any? to String?
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
            )

            if (!rateLimitResult.allowed) {
                logger.warn { "Rate limit exceeded for client: $clientId on $path" }
                return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Try again later.")
            }

            // Check authentication for protected endpoints
            if (requiresAuthentication(path) && user == null) {
                logger.warn { "Unauthenticated access attempt to $path" }
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Authentication required")
            }

            // Check authorization for role-based endpoints
            if (user != null && !hasPermission(user, path, method)) {
                logger.warn { "Unauthorized access attempt by ${user.username} to $path" }
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Access denied")
            }

            // Route to appropriate service
            routeToService(path, method, headers, body)

        } catch (e: Exception) {
            logger.error(e) { "Routing error for $method $path" }
            ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Gateway routing error: ${e.message}")
        }
    }

    // Helper methods
    private fun extractTokenFromHeader(authorization: String?): String {
        if (authorization.isNullOrBlank() || !authorization.startsWith("Bearer ")) {
            throw IllegalArgumentException("Invalid authorization header")
        }
        return authorization.substring(7)
    }

    private suspend fun validateAdminAccess(authorization: String?): UserInfo {
        val token = extractTokenFromHeader(authorization)
        val user = authenticationService.validateToken(token)
            ?: throw SecurityException("Invalid or expired token")

        if (user.role != "ADMIN" && user.role != "ATTESTATION_AUTHORITY") {
            throw SecurityException("Admin access required")
        }

        return user
    }

    private suspend fun extractUserFromHeaders(headers: HttpHeaders): UserInfo? {
        val authorization = headers.getFirst("Authorization")
        return if (authorization != null) {
            try {
                val token = extractTokenFromHeader(authorization)
                authenticationService.validateToken(token)
            } catch (e: Exception) {
                logger.debug { "Failed to extract user from headers: ${e.message}" }
                null
            }
        } else null
    }

    private fun getClientId(request: HttpServletRequest, headers: HttpHeaders): String {
        // Try to get client ID from various sources
        return headers.getFirst("X-Client-ID")
            ?: request.remoteAddr
            ?: "unknown"
    }

    private fun requiresAuthentication(path: String): Boolean {
        val publicPaths = setOf(
            "/",
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/health",
            "/api/v1/verification/verify" // Public verification endpoint
        )
        return !publicPaths.contains(path) && !path.startsWith("/api/v1/public/")
    }

    private fun hasPermission(user: UserInfo, path: String, method: HttpMethod): Boolean {
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

    private suspend fun routeToService(
        path: String,
        method: HttpMethod,
        headers: HttpHeaders,
        body: String?
    ): ResponseEntity<String> {
        return when {
            path.startsWith("/api/v1/universities") ||
                    path.startsWith("/api/v1/governance") ||
                    path.startsWith("/api/v1/revenue") ||
                    path.startsWith("/api/v1/compliance") -> {
                routingService.routeToAttestationAuthority(path, method, headers, body)
            }

            path.startsWith("/api/v1/degrees") ||
                    path.startsWith("/api/v1/students") -> {
                routingService.routeToUniversityPortal(path, method, headers, body)
            }

            path.startsWith("/api/v1/verification") ||
                    path.startsWith("/api/v1/payments") ||
                    path.startsWith("/api/v1/audit") -> {
                routingService.routeToEmployerPortal(path, method, headers, body)
            }

            else -> {
                ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Service not found for path: $path")
            }
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
}
