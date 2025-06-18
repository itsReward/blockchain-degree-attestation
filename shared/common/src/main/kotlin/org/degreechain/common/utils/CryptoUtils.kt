package org.degreechain.common.utils

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
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

    fun generateSecretKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance("AES")
        keyGenerator.init(256)
        return keyGenerator.generateKey()
    }

    fun encryptAES(data: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedData = cipher.doFinal(data.toByteArray())
        return Base64.getEncoder().encodeToString(encryptedData)
    }

    fun decryptAES(encryptedData: String, secretKey: SecretKey): String {
        val cipher = Cipher.getInstance("AES")
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        val decodedData = Base64.getDecoder().decode(encryptedData)
        val decryptedData = cipher.doFinal(decodedData)
        return String(decryptedData)
    }

    fun generateKeyFromString(key: String): SecretKey {
        val hash = generateSHA256Hash(key)
        val keyBytes = hash.substring(0, 32).toByteArray() // Use first 32 characters (256 bits)
        return SecretKeySpec(keyBytes, "AES")
    }

    fun verifyHash(input: String, expectedHash: String): Boolean {
        val actualHash = generateSHA256Hash(input)
        return actualHash.equals(expectedHash, ignoreCase = true)
    }
}