package org.degreechain.utils

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object CryptoUtils {

    private val secureRandom = SecureRandom()

    fun generateSHA256Hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun generateSHA256Hash(input: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input)
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun generateRandomString(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return (1..length)
            .map { chars[secureRandom.nextInt(chars.length)] }
            .joinToString("")
    }

    fun generateUUID(): String {
        return java.util.UUID.randomUUID().toString()
    }

    fun verifyHash(input: String, expectedHash: String): Boolean {
        val actualHash = generateSHA256Hash(input)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }

    fun encodeBase64(input: ByteArray): String {
        return Base64.getEncoder().encodeToString(input)
    }

    fun decodeBase64(input: String): ByteArray {
        return Base64.getDecoder().decode(input)
    }
}