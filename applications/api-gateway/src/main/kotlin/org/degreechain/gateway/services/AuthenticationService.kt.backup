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

            // Check if user is locked
            if (user.isLocked) {
                logger.warn { "Authentication attempt for locked user: ${request.username}" }
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Account is locked due to too many failed login attempts"
                )
            }

            // Check if user is active
            if (!user.isActive) {
                logger.warn { "Authentication attempt for inactive user: ${request.username}" }
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Account is disabled"
                )
            }

            // Verify password
            if (!passwordEncoder.matches(request.password, user.passwordHash)) {
                logger.warn { "Invalid password for user: ${request.username}" }

                // Increment failed login attempts
                val updatedUser = user.copy(
                    failedLoginAttempts = user.failedLoginAttempts + 1,
                    isLocked = user.failedLoginAttempts + 1 >= 5 // Lock after 5 failed attempts
                )
                users[user.userId] = updatedUser

                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid username or password"
                )
            }

            // Check organization code if provided
            if (request.organizationCode != null && user.organizationCode != request.organizationCode) {
                logger.warn { "Invalid organization code for user: ${request.username}" }
                return@withContext AuthenticationResponse(
                    success = false,
                    accessToken = null,
                    refreshToken = null,
                    user = null,
                    message = "Invalid organization code"
                )
            }

            // Generate tokens
            val accessToken = jwtTokenProvider.generateAccessToken(user.userId, user.role, user.organizationCode)
            val refreshToken = jwtTokenProvider.generateRefreshToken(user.userId)

            // Store tokens
            activeTokens[accessToken] = user.userId
            refreshTokens[refreshToken] = user.userId

            // Update user last login and reset failed attempts
            val updatedUser = user.copy(
                lastLoginAt = LocalDateTime.now(),
                failedLoginAttempts = 0,
                isLocked = false
            )
            users[user.userId] = updatedUser

            logger.info { "Successful authentication for user: ${request.username}" }

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
            logger.error(e) { "Authentication error for user: ${request.username}" }
            AuthenticationResponse(
                success = false,
                accessToken = null,
                refreshToken = null,
                user = null,
                message = "Authentication failed due to technical error"
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
    ): User = withContext(Dispatchers.IO) {
        logger.info { "Creating new user: $username" }

        // Check if username already exists
        if (findUserByUsername(username) != null) {
            throw BusinessException(
                "Username already exists: $username",
                ErrorCode.VALIDATION_ERROR
            )
        }

        // Check if email already exists
        if (findUserByEmail(email) != null) {
            throw BusinessException(
                "Email already exists: $email",
                ErrorCode.VALIDATION_ERROR
            )
        }

        val userId = UUID.randomUUID().toString()
        val user = User(
            userId = userId,
            username = username,
            email = email,
            passwordHash = passwordEncoder.encode(password),
            role = role,
            organizationCode = organizationCode,
            isActive = true,
            createdAt = LocalDateTime.now(),
            lastLoginAt = null
        )

        users[userId] = user
        logger.info { "User created successfully: $username" }

        user
    }

    suspend fun updateUserRole(userId: String, newRole: String): Boolean = withContext(Dispatchers.IO) {
        logger.info { "Updating role for user: $userId to $newRole" }

        val user = users[userId] ?: return@withContext false

        val updatedUser = user.copy(role = newRole)
        users[userId] = updatedUser

        // Invalidate all tokens for this user to force re-authentication with new role
        activeTokens.entries.removeIf { it.value == userId }
        refreshTokens.entries.removeIf { it.value == userId }

        logger.info { "User role updated successfully: $userId" }
        true
    }

    suspend fun lockUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        logger.warn { "Locking user: $userId" }

        val user = users[userId] ?: return@withContext false

        val updatedUser = user.copy(isLocked = true)
        users[userId] = updatedUser

        // Invalidate all tokens for this user
        activeTokens.entries.removeIf { it.value == userId }
        refreshTokens.entries.removeIf { it.value == userId }

        logger.warn { "User locked successfully: $userId" }
        true
    }

    suspend fun unlockUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        logger.info { "Unlocking user: $userId" }

        val user = users[userId] ?: return@withContext false

        val updatedUser = user.copy(
            isLocked = false,
            failedLoginAttempts = 0
        )
        users[userId] = updatedUser

        logger.info { "User unlocked successfully: $userId" }
        true
    }

    suspend fun getAuthenticationStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        val now = LocalDateTime.now()
        val recentLogins = users.values.count { user ->
            user.lastLoginAt?.isAfter(now.minusHours(24)) == true
        }

        val lockedUsers = users.values.count { it.isLocked }
        val activeUsers = users.values.count { it.isActive }
        val roleDistribution = users.values.groupingBy { it.role }.eachCount()

        mapOf(
            "totalUsers" to users.size,
            "activeUsers" to activeUsers,
            "lockedUsers" to lockedUsers,
            "recentLogins24h" to recentLogins,
            "activeTokens" to activeTokens.size,
            "activeRefreshTokens" to refreshTokens.size,
            "roleDistribution" to roleDistribution,
            "timestamp" to now.toString()
        )
    }

    private fun findUserByUsername(username: String): User? {
        return users.values.find { it.username.equals(username, ignoreCase = true) }
    }

    private fun findUserByEmail(email: String): User? {
        return users.values.find { it.email.equals(email, ignoreCase = true) }
    }

    private fun getPermissionsForRole(role: String): List<String> {
        return when (role.uppercase()) {
            "ADMIN" -> listOf(
                "READ_ALL", "WRITE_ALL", "DELETE_ALL", "MANAGE_USERS",
                "MANAGE_UNIVERSITIES", "VIEW_ANALYTICS", "MANAGE_SYSTEM"
            )
            "ATTESTATION_AUTHORITY" -> listOf(
                "READ_UNIVERSITIES", "WRITE_UNIVERSITIES", "MANAGE_UNIVERSITIES",
                "VIEW_REVENUE", "MANAGE_GOVERNANCE", "VIEW_COMPLIANCE"
            )
            "UNIVERSITY" -> listOf(
                "READ_DEGREES", "WRITE_DEGREES", "MANAGE_STUDENTS",
                "VIEW_REVENUE", "SUBMIT_DEGREES"
            )
            "EMPLOYER" -> listOf(
                "VERIFY_DEGREES", "VIEW_VERIFICATION_HISTORY",
                "MAKE_PAYMENTS", "VIEW_AUDIT_TRAIL"
            )
            else -> listOf("READ_BASIC")
        }
    }

    private fun initializeDefaultUsers() {
        // Create default admin user
        val adminId = UUID.randomUUID().toString()
        users[adminId] = User(
            userId = adminId,
            username = "admin",
            email = "admin@degreechain.org",
            passwordHash = passwordEncoder.encode("admin123"),
            role = "ADMIN",
            organizationCode = null,
            isActive = true,
            createdAt = LocalDateTime.now(),
            lastLoginAt = null
        )

        // Create default attestation authority user
        val authorityId = UUID.randomUUID().toString()
        users[authorityId] = User(
            userId = authorityId,
            username = "authority",
            email = "authority@degreechain.org",
            passwordHash = passwordEncoder.encode("authority123"),
            role = "ATTESTATION_AUTHORITY",
            organizationCode = "ATTESTATION_AUTHORITY",
            isActive = true,
            createdAt = LocalDateTime.now(),
            lastLoginAt = null
        )

        // Create default university user
        val universityId = UUID.randomUUID().toString()
        users[universityId] = User(
            userId = universityId,
            username = "university",
            email = "admin@university.edu",
            passwordHash = passwordEncoder.encode("university123"),
            role = "UNIVERSITY",
            organizationCode = "UNI001",
            isActive = true,
            createdAt = LocalDateTime.now(),
            lastLoginAt = null
        )

        // Create default employer user
        val employerId = UUID.randomUUID().toString()
        users[employerId] = User(
            userId = employerId,
            username = "employer",
            email = "hr@company.com",
            passwordHash = passwordEncoder.encode("employer123"),
            role = "EMPLOYER",
            organizationCode = "EMP001",
            isActive = true,
            createdAt = LocalDateTime.now(),
            lastLoginAt = null
        )

        logger.info { "Initialized ${users.size} default users" }
    }
}