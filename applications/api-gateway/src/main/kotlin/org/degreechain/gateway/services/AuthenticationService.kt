package org.degreechain.gateway.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.gateway.security.JwtTokenProvider
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

data class User(
    val userId: String,
    val username: String,
    val email: String,
    val passwordHash: String,
    val role: String,
    val organizationCode: String?,
    val isActive: Boolean,
    val createdAt: LocalDateTime,
    val lastLoginAt: LocalDateTime?,
    val failedLoginAttempts: Int = 0,
    val isLocked: Boolean = false,
    val metadata: Map<String, Any> = emptyMap()
)

data class AuthenticationRequest(
    val username: String,
    val password: String,
    val organizationCode: String? = null
)

data class AuthenticationResponse(
    val success: Boolean,
    val accessToken: String?,
    val refreshToken: String?,
    val user: UserInfo?,
    val message: String,
    val expiresIn: Long? = null
)

data class UserInfo(
    val userId: String,
    val username: String,
    val email: String,
    val role: String,
    val organizationCode: String?,
    val permissions: List<String>
)

@Service
class AuthenticationService(
    private val passwordEncoder: PasswordEncoder,
    private val jwtTokenProvider: JwtTokenProvider
) {

    // In-memory storage for demo purposes - in production this would be a database
    private val users = ConcurrentHashMap<String, User>()
    private val refreshTokens = ConcurrentHashMap<String, String>() // refresh token -> userId
    private val activeTokens = ConcurrentHashMap<String, String>() // access token -> userId

    init {
        // Initialize with default users
        initializeDefaultUsers()
    }

    suspend fun authenticate(request: AuthenticationRequest): AuthenticationResponse = withContext(Dispatchers.IO) {
        logger.info { "Authentication attempt for user: ${request.username}" }

        try {
            val user = findUserByUsername(request.username)
                ?: return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid username or password"
                )

            if (!user.isActive) {
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Account is disabled"
                )
            }

            if (user.isLocked) {
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Account is locked due to multiple failed login attempts"
                )
            }

            if (!passwordEncoder.matches(request.password, user.passwordHash)) {
                // Increment failed login attempts
                incrementFailedLoginAttempts(user.userId)
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid username or password"
                )
            }

            // Reset failed login attempts on successful authentication
            resetFailedLoginAttempts(user.userId)

            // Generate tokens
            val accessToken = jwtTokenProvider.generateAccessToken(user.userId, user.role, user.organizationCode)
            val refreshToken = jwtTokenProvider.generateRefreshToken(user.userId)

            // Store tokens
            activeTokens[accessToken] = user.userId
            refreshTokens[refreshToken] = user.userId

            logger.info { "Authentication successful for user: ${user.username}" }

            AuthenticationResponse(
                success = true,
                accessToken = accessToken,
                refreshToken = refreshToken,
                user = UserInfo(
                    userId = user.userId,
                    username = user.username,
                    email = user.email,
                    role = user.role,
                    organizationCode = user.organizationCode,
                    permissions = getPermissionsForRole(user.role)
                ),
                message = "Authentication successful",
                expiresIn = jwtTokenProvider.getAccessTokenExpirationTime()
            )

        } catch (e: Exception) {
            logger.error(e) { "Authentication error" }
            AuthenticationResponse(
                success = false,
                accessToken = null,
                refreshToken = null,
                user = null,
                message = "Authentication failed"
            )
        }
    }

    suspend fun refreshToken(refreshToken: String): AuthenticationResponse = withContext(Dispatchers.IO) {
        logger.debug { "Token refresh attempt" }

        try {
            val userId = refreshTokens[refreshToken]
                ?: return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid refresh token"
                )

            if (!jwtTokenProvider.validateToken(refreshToken) || !jwtTokenProvider.isRefreshToken(refreshToken)) {
                refreshTokens.remove(refreshToken)
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid refresh token"
                )
            }

            val user = users[userId]
                ?: return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "User not found"
                )

            if (!user.isActive || user.isLocked) {
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Account is disabled or locked"
                )
            }

            // Generate new tokens
            val newAccessToken = jwtTokenProvider.generateAccessToken(user.userId, user.role, user.organizationCode)
            val newRefreshToken = jwtTokenProvider.generateRefreshToken(user.userId)

            // Remove old tokens and store new ones
            refreshTokens.remove(refreshToken)
            activeTokens[newAccessToken] = user.userId
            refreshTokens[newRefreshToken] = user.userId

            logger.debug { "Token refresh successful for user: ${user.username}" }

            AuthenticationResponse(
                success = true,
                accessToken = newAccessToken,
                refreshToken = newRefreshToken,
                user = UserInfo(
                    userId = user.userId,
                    username = user.username,
                    email = user.email,
                    role = user.role,
                    organizationCode = user.organizationCode,
                    permissions = getPermissionsForRole(user.role)
                ),
                message = "Token refreshed successfully",
                expiresIn = jwtTokenProvider.getAccessTokenExpirationTime()
            )

        } catch (e: Exception) {
            logger.error(e) { "Token refresh error" }
            AuthenticationResponse(
                success = false,
                accessToken = null,
                refreshToken = null,
                user = null,
                message = "Token refresh failed"
            )
        }
    }

    suspend fun validateToken(accessToken: String): UserInfo? = withContext(Dispatchers.IO) {
        try {
            // Check if token is in active tokens
            val userId = activeTokens[accessToken] ?: return@withContext null

            // Validate with JWT provider
            if (!jwtTokenProvider.validateToken(accessToken)) {
                activeTokens.remove(accessToken)
                return@withContext null
            }

            val user = users[userId] ?: return@withContext null

            if (!user.isActive || user.isLocked) {
                activeTokens.remove(accessToken)
                return@withContext null
            }

            UserInfo(
                userId = user.userId,
                username = user.username,
                email = user.email,
                role = user.role,
                organizationCode = user.organizationCode,
                permissions = getPermissionsForRole(user.role)
            )

        } catch (e: Exception) {
            logger.error(e) { "Token validation error" }
            null
        }
    }

    suspend fun logout(accessToken: String): Boolean = withContext(Dispatchers.IO) {
        logger.debug { "Logout attempt" }

        val userId = activeTokens.remove(accessToken)
        if (userId != null) {
            // Remove all refresh tokens for this user
            refreshTokens.entries.removeIf { it.value == userId }
            logger.info { "User logged out successfully: $userId" }
            true
        } else {
            false
        }
    }

    suspend fun createUser(
        username: String,
        email: String,
        password: String,
        role: String,
        organizationCode: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val userId = UUID.randomUUID().toString()
            val passwordHash = passwordEncoder.encode(password)

            val user = User(
                userId = userId,
                username = username,
                email = email,
                passwordHash = passwordHash,
                role = role,
                organizationCode = organizationCode,
                isActive = true,
                createdAt = LocalDateTime.now(),
                lastLoginAt = null
            )

            users[userId] = user
            logger.info { "User created successfully: $username" }
            true

        } catch (e: Exception) {
            logger.error(e) { "Error creating user: $username" }
            false
        }
    }

    suspend fun lockUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = users[userId] ?: return@withContext false

            val lockedUser = user.copy(isLocked = true)
            users[userId] = lockedUser

            // Remove all active tokens for this user
            activeTokens.entries.removeIf { it.value == userId }
            refreshTokens.entries.removeIf { it.value == userId }

            logger.info { "User locked successfully: ${user.username}" }
            true

        } catch (e: Exception) {
            logger.error(e) { "Error locking user: $userId" }
            false
        }
    }

    suspend fun unlockUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val user = users[userId] ?: return@withContext false

            val unlockedUser = user.copy(isLocked = false, failedLoginAttempts = 0)
            users[userId] = unlockedUser

            logger.info { "User unlocked successfully: ${user.username}" }
            true

        } catch (e: Exception) {
            logger.error(e) { "Error unlocking user: $userId" }
            false
        }
    }

    suspend fun getAuthenticationStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        mapOf(
            "totalUsers" to users.size,
            "activeUsers" to users.values.count { it.isActive },
            "lockedUsers" to users.values.count { it.isLocked },
            "activeTokens" to activeTokens.size,
            "refreshTokens" to refreshTokens.size,
            "usersByRole" to users.values.groupBy { it.role }.mapValues { it.value.size },
            "timestamp" to System.currentTimeMillis()
        )
    }

    private fun findUserByUsername(username: String): User? {
        return users.values.find { it.username == username }
    }

    private fun incrementFailedLoginAttempts(userId: String) {
        val user = users[userId] ?: return
        val updatedUser = user.copy(
            failedLoginAttempts = user.failedLoginAttempts + 1,
            isLocked = user.failedLoginAttempts + 1 >= 5 // Lock after 5 failed attempts
        )
        users[userId] = updatedUser
    }

    private fun resetFailedLoginAttempts(userId: String) {
        val user = users[userId] ?: return
        val updatedUser = user.copy(failedLoginAttempts = 0)
        users[userId] = updatedUser
    }

    private fun getPermissionsForRole(role: String): List<String> {
        return when (role) {
            "ADMIN" -> listOf(
                "READ_ALL", "WRITE_ALL", "DELETE_ALL",
                "MANAGE_USERS", "MANAGE_SYSTEM", "VIEW_STATISTICS"
            )
            "ATTESTATION_AUTHORITY" -> listOf(
                "READ_UNIVERSITIES", "WRITE_UNIVERSITIES",
                "READ_GOVERNANCE", "WRITE_GOVERNANCE",
                "READ_REVENUE", "WRITE_REVENUE",
                "READ_COMPLIANCE", "WRITE_COMPLIANCE"
            )
            "UNIVERSITY" -> listOf(
                "READ_DEGREES", "WRITE_DEGREES",
                "READ_STUDENTS", "WRITE_STUDENTS",
                "READ_REVENUE"
            )
            "EMPLOYER" -> listOf(
                "READ_VERIFICATION", "WRITE_VERIFICATION",
                "READ_PAYMENTS", "WRITE_PAYMENTS",
                "READ_AUDIT"
            )
            else -> emptyList()
        }
    }

    private fun initializeDefaultUsers() {
        try {
            // Admin user
            val adminUserId = UUID.randomUUID().toString()
            users[adminUserId] = User(
                userId = adminUserId,
                username = "admin",
                email = "admin@degreechain.org",
                passwordHash = passwordEncoder.encode("admin123"),
                role = "ADMIN",
                organizationCode = null,
                isActive = true,
                createdAt = LocalDateTime.now(),
                lastLoginAt = null
            )

            // Attestation Authority user
            val attestationUserId = UUID.randomUUID().toString()
            users[attestationUserId] = User(
                userId = attestationUserId,
                username = "attestation",
                email = "attestation@degreechain.org",
                passwordHash = passwordEncoder.encode("attestation123"),
                role = "ATTESTATION_AUTHORITY",
                organizationCode = "ATTEST_001",
                isActive = true,
                createdAt = LocalDateTime.now(),
                lastLoginAt = null
            )

            // University user
            val universityUserId = UUID.randomUUID().toString()
            users[universityUserId] = User(
                userId = universityUserId,
                username = "university",
                email = "university@example.edu",
                passwordHash = passwordEncoder.encode("university123"),
                role = "UNIVERSITY",
                organizationCode = "UNI_001",
                isActive = true,
                createdAt = LocalDateTime.now(),
                lastLoginAt = null
            )

            // Employer user
            val employerUserId = UUID.randomUUID().toString()
            users[employerUserId] = User(
                userId = employerUserId,
                username = "employer",
                email = "employer@company.com",
                passwordHash = passwordEncoder.encode("employer123"),
                role = "EMPLOYER",
                organizationCode = "EMP_001",
                isActive = true,
                createdAt = LocalDateTime.now(),
                lastLoginAt = null
            )

            logger.info { "Default users initialized successfully" }

        } catch (e: Exception) {
            logger.error(e) { "Error initializing default users" }
        }
    }

    fun getAccessTokenExpirationTime(): Long {
        return jwtTokenProvider.getAccessTokenExpirationTime()
    }

    suspend fun getAuthenticationStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val activeUsers = activeTokens.size
        val totalUsers = users.size
        val lockedUsers = users.values.count { it.isLocked }

        mapOf(
            "totalUsers" to totalUsers,
            "activeUsers" to activeUsers,
            "lockedUsers" to lockedUsers,
            "refreshTokensActive" to refreshTokens.size,
            "authenticationSuccessRate" to 0.95, // This would be calculated from real metrics
            "averageSessionDuration" to "45 minutes", // This would be calculated from real data
            "timestamp" to now.toString()
        )
    }
}