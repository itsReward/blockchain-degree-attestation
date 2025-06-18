package org.degreechain.gateway.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

private val logger = KotlinLogging.logger {}

@Component
class JwtTokenProvider {

    @Value("\${jwt.secret:mySecretKey12345678901234567890123456789012345678901234567890}")
    private lateinit var jwtSecret: String

    @Value("\${jwt.access-token-expiration:3600000}") // 1 hour
    private var accessTokenExpiration: Long = 3600000

    @Value("\${jwt.refresh-token-expiration:2592000000}") // 30 days
    private var refreshTokenExpiration: Long = 2592000000

    @Value("\${jwt.issuer:degreechain-gateway}")
    private lateinit var issuer: String

    private val key: Key by lazy {
        Keys.hmacShaKeyFor(jwtSecret.toByteArray())
    }

    fun generateAccessToken(
        userId: String,
        role: String,
        organizationCode: String? = null
    ): String {
        logger.debug { "Generating access token for user: $userId, role: $role" }

        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)

        val claims = Jwts.claims().setSubject(userId)
        claims["role"] = role
        claims["type"] = "access"
        organizationCode?.let { claims["organizationCode"] = it }

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun generateRefreshToken(userId: String): String {
        logger.debug { "Generating refresh token for user: $userId" }

        val now = Date()
        val expiryDate = Date(now.time + refreshTokenExpiration)

        val claims = Jwts.claims().setSubject(userId)
        claims["type"] = "refresh"

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)

            val expiration = claims.body.expiration
            val now = Date()

            val isValid = expiration.after(now)

            if (!isValid) {
                logger.debug { "Token expired: ${claims.body.subject}" }
            }

            isValid
        } catch (e: JwtException) {
            logger.debug(e) { "Invalid JWT token" }
            false
        } catch (e: IllegalArgumentException) {
            logger.debug(e) { "Invalid JWT token argument" }
            false
        }
    }

    fun getUserIdFromToken(token: String): String? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims.subject
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting user ID from token" }
            null
        }
    }

    fun getRoleFromToken(token: String): String? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims["role"] as? String
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting role from token" }
            null
        }
    }

    fun getOrganizationCodeFromToken(token: String): String? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims["organizationCode"] as? String
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting organization code from token" }
            null
        }
    }

    fun getTokenTypeFromToken(token: String): String? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims["type"] as? String
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting token type from token" }
            null
        }
    }

    fun getExpirationFromToken(token: String): Date? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims.expiration
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting expiration from token" }
            null
        }
    }

    fun getIssuedAtFromToken(token: String): Date? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims.issuedAt
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting issued at from token" }
            null
        }
    }

    fun getTokenIdFromToken(token: String): String? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            claims.id
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting token ID from token" }
            null
        }
    }

    fun isTokenExpired(token: String): Boolean {
        val expiration = getExpirationFromToken(token)
        return expiration?.before(Date()) ?: true
    }

    fun isRefreshToken(token: String): Boolean {
        return getTokenTypeFromToken(token) == "refresh"
    }

    fun isAccessToken(token: String): Boolean {
        return getTokenTypeFromToken(token) == "access"
    }

    fun getAccessTokenExpirationTime(): Long {
        return accessTokenExpiration / 1000 // Return in seconds
    }

    fun getRefreshTokenExpirationTime(): Long {
        return refreshTokenExpiration / 1000 // Return in seconds
    }

    fun getRemainingTimeFromToken(token: String): Long? {
        return try {
            val expiration = getExpirationFromToken(token)
            expiration?.let {
                val remaining = it.time - System.currentTimeMillis()
                if (remaining > 0) remaining / 1000 else 0 // Return in seconds
            }
        } catch (e: Exception) {
            logger.debug(e) { "Error calculating remaining time from token" }
            null
        }
    }

    fun getAllClaimsFromToken(token: String): Map<String, Any>? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            mapOf(
                "subject" to (claims.subject ?: ""),
                "role" to (claims["role"] ?: ""),
                "organizationCode" to (claims["organizationCode"] ?: ""),
                "type" to (claims["type"] ?: ""),
                "issuedAt" to claims.issuedAt,
                "expiration" to claims.expiration,
                "issuer" to (claims.issuer ?: ""),
                "tokenId" to (claims.id ?: "")
            )
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting all claims from token" }
            null
        }
    }

    fun refreshAccessToken(refreshToken: String, newRole: String? = null): String? {
        return try {
            if (!validateToken(refreshToken) || !isRefreshToken(refreshToken)) {
                logger.debug { "Invalid or non-refresh token provided for refresh" }
                return null
            }

            val userId = getUserIdFromToken(refreshToken)
            val organizationCode = getOrganizationCodeFromToken(refreshToken)
            val role = newRole ?: getRoleFromToken(refreshToken)

            if (userId != null && role != null) {
                generateAccessToken(userId, role, organizationCode)
            } else {
                logger.debug { "Missing required claims in refresh token" }
                null
            }
        } catch (e: Exception) {
            logger.debug(e) { "Error refreshing access token" }
            null
        }
    }

    fun validateTokenStructure(token: String): Boolean {
        return try {
            val parts = token.split(".")
            parts.size == 3 && parts.all { it.isNotEmpty() }
        } catch (e: Exception) {
            false
        }
    }

    fun generateTokenForApiKey(apiKey: String, permissions: List<String>): String {
        logger.debug { "Generating token for API key authentication" }

        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)

        val claims = Jwts.claims().setSubject("api-key:$apiKey")
        claims["type"] = "api-key"
        claims["permissions"] = permissions

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun getPermissionsFromToken(token: String): List<String>? {
        return try {
            val claims = Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .body

            @Suppress("UNCHECKED_CAST")
            claims["permissions"] as? List<String>
        } catch (e: JwtException) {
            logger.debug(e) { "Error extracting permissions from token" }
            null
        }
    }
}