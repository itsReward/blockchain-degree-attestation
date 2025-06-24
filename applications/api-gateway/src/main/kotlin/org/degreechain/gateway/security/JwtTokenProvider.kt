package org.degreechain.gateway.security

import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import mu.KotlinLogging
import org.degreechain.gateway.security.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*
import javax.crypto.spec.SecretKeySpec

private val logger = KotlinLogging.logger {}

@Component
class JwtTokenProvider {

    @Value("\${jwt.secret}")
    private lateinit var jwtSecret: String

    @Value("\${jwt.access-token-expiration}")
    private var accessTokenExpiration: Long = 3600000 // 1 hour

    @Value("\${jwt.refresh-token-expiration}")
    private var refreshTokenExpiration: Long = 2592000000 // 30 days

    @Value("\${jwt.issuer}")
    private lateinit var issuer: String

    private val key: Key by lazy {
        SecretKeySpec(jwtSecret.toByteArray(), SignatureAlgorithm.HS512.jcaName)
    }

    fun generateAccessToken(
        userId: String,
        role: String,
        organizationCode: String? = null
    ): String {
        logger.debug { "Generating access token for user: $userId, role: $role" }

        val now = Date()
        val expiryDate = Date(now.time + accessTokenExpiration)

        return Jwts.builder()
            .setSubject(userId)
            .claim("role", role)
            .claim("type", "access")
            .apply { organizationCode?.let { claim("organizationCode", it) } }
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

        return Jwts.builder()
            .setSubject(userId)
            .claim("type", "refresh")
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            val claims = Jwts.parser()
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
            val claims = Jwts.parser()
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
            val claims = Jwts.parser()
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
            val claims = Jwts.parser()
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
            val claims = Jwts.parser()
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

    fun isRefreshToken(token: String): Boolean {
        return getTokenTypeFromToken(token) == "refresh"
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

        return Jwts.builder()
            .setSubject("api-key:$apiKey")
            .claim("type", "api-key")
            .claim("permissions", permissions)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(issuer)
            .setId(UUID.randomUUID().toString())
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun getPermissionsFromToken(token: String): List<String>? {
        return try {
            val claims = Jwts.parser()
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

    /**
     * Returns the access token expiration time in seconds
     * @return Access token expiration time in seconds
     */
    fun getAccessTokenExpirationTime(): Long {
        return accessTokenExpiration / 1000 // Convert milliseconds to seconds
    }
}
