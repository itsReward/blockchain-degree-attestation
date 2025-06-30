package org.degreechain.gateway.utils

import org.degreechain.gateway.models.FileUploadMetadata
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.LocalDateTime
import java.util.*
import org.slf4j.LoggerFactory

/**
 * Utility class for file operations and validation
 */
object FileUtils {
    private val logger = LoggerFactory.getLogger(FileUtils::class.java)

    // Supported file types
    private val ALLOWED_MIME_TYPES = setOf(
        "image/jpeg",
        "image/jpg",
        "image/png",
        "image/gif",
        "application/pdf"
    )

    private val ALLOWED_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "pdf"
    )

    // File size limits
    private const val MAX_FILE_SIZE = 10 * 1024 * 1024L // 10MB
    private const val MIN_FILE_SIZE = 1024L // 1KB

    /**
     * Validate uploaded certificate file
     */
    fun validateCertificateFile(file: MultipartFile): ValidationResult {
        try {
            // Check if file is empty
            if (file.isEmpty) {
                return ValidationResult(false, "File cannot be empty")
            }

            // Check file size
            if (file.size > MAX_FILE_SIZE) {
                return ValidationResult(false, "File size exceeds maximum allowed size of ${MAX_FILE_SIZE / (1024 * 1024)}MB")
            }

            if (file.size < MIN_FILE_SIZE) {
                return ValidationResult(false, "File size is too small. Minimum size is ${MIN_FILE_SIZE / 1024}KB")
            }

            // Check filename
            val filename = file.originalFilename
            if (filename.isNullOrBlank()) {
                return ValidationResult(false, "Filename cannot be empty")
            }

            // Check file extension
            val extension = getFileExtension(filename).lowercase()
            if (!ALLOWED_EXTENSIONS.contains(extension)) {
                return ValidationResult(false, "File type not supported. Allowed types: ${ALLOWED_EXTENSIONS.joinToString(", ")}")
            }

            // Check MIME type
            val mimeType = file.contentType
            if (mimeType.isNullOrBlank() || !ALLOWED_MIME_TYPES.contains(mimeType.lowercase())) {
                return ValidationResult(false, "Invalid file type. Expected: ${ALLOWED_MIME_TYPES.joinToString(", ")}")
            }

            // Check for suspicious filenames
            if (containsSuspiciousPatterns(filename)) {
                return ValidationResult(false, "Filename contains invalid characters")
            }

            // Validate file header/magic bytes
            val headerValidation = validateFileHeader(file, extension)
            if (!headerValidation.isValid) {
                return headerValidation
            }

            logger.info("File validation passed: $filename (${file.size} bytes)")
            return ValidationResult(true, "File validation successful")

        } catch (e: Exception) {
            logger.error("Error validating file", e)
            return ValidationResult(false, "File validation failed: ${e.message}")
        }
    }

    /**
     * Generate temporary filename with timestamp and UUID
     */
    fun generateTempFileName(originalFilename: String): String {
        val extension = getFileExtension(originalFilename)
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "${timestamp}_${uuid}.$extension"
    }

    /**
     * Clean up temporary files older than specified duration
     */
    fun cleanupTempFiles(directory: Path, olderThan: Duration) {
        try {
            if (!Files.exists(directory)) {
                return
            }

            val cutoffTime = LocalDateTime.now().minus(olderThan)
            var deletedCount = 0

            Files.walk(directory)
                .filter { Files.isRegularFile(it) }
                .filter { path ->
                    try {
                        val lastModified = Files.getLastModifiedTime(path).toInstant()
                        lastModified.isBefore(cutoffTime.atZone(java.time.ZoneId.systemDefault()).toInstant())
                    } catch (e: Exception) {
                        logger.warn("Error checking file modification time: ${path.fileName}", e)
                        false
                    }
                }
                .forEach { path ->
                    try {
                        Files.deleteIfExists(path)
                        deletedCount++
                    } catch (e: Exception) {
                        logger.error("Error deleting temporary file: ${path.fileName}", e)
                    }
                }

            if (deletedCount > 0) {
                logger.info("Cleaned up $deletedCount temporary files older than $olderThan")
            }

        } catch (e: Exception) {
            logger.error("Error during temporary file cleanup", e)
        }
    }

    /**
     * Extract file metadata
     */
    fun extractFileMetadata(file: MultipartFile, uploadedBy: String? = null): FileUploadMetadata {
        return FileUploadMetadata(
            filename = file.originalFilename ?: "unknown",
            fileSize = file.size,
            mimeType = file.contentType ?: "unknown",
            uploadedAt = LocalDateTime.now(),
            uploadedBy = uploadedBy
        )
    }

    /**
     * Check if file is an image
     */
    fun isImageFile(mimeType: String): Boolean {
        return mimeType.lowercase().startsWith("image/")
    }

    /**
     * Check if file is a PDF
     */
    fun isPdfFile(mimeType: String): Boolean {
        return mimeType.lowercase() == "application/pdf"
    }

    /**
     * Get file extension from filename
     */
    fun getFileExtension(filename: String): String {
        val lastDotIndex = filename.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < filename.length - 1) {
            filename.substring(lastDotIndex + 1)
        } else {
            ""
        }
    }

    /**
     * Create safe filename by removing/replacing invalid characters
     */
    fun createSafeFilename(filename: String): String {
        return filename
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .replace(Regex("_{2,}"), "_")
            .take(255) // Limit filename length
    }

    /**
     * Get human-readable file size
     */
    fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = bytes.toDouble()
        var unitIndex = 0

        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }

        return "%.1f %s".format(size, units[unitIndex])
    }

    /**
     * Create directory if it doesn't exist
     */
    fun ensureDirectoryExists(path: Path): Boolean {
        return try {
            if (!Files.exists(path)) {
                Files.createDirectories(path)
                logger.info("Created directory: $path")
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to create directory: $path", e)
            false
        }
    }

    /**
     * Move file to target directory with unique name if conflict exists
     */
    fun moveFileWithUniqueNameIfConflict(sourcePath: Path, targetDir: Path, preferredName: String): Path {
        ensureDirectoryExists(targetDir)

        var targetPath = targetDir.resolve(preferredName)
        var counter = 1

        while (Files.exists(targetPath)) {
            val extension = getFileExtension(preferredName)
            val nameWithoutExt = preferredName.substringBeforeLast('.')
            val newName = if (extension.isNotBlank()) {
                "${nameWithoutExt}_$counter.$extension"
            } else {
                "${preferredName}_$counter"
            }
            targetPath = targetDir.resolve(newName)
            counter++
        }

        Files.move(sourcePath, targetPath)
        logger.info("Moved file to: $targetPath")
        return targetPath
    }

    // ========== PRIVATE HELPER METHODS ==========

    private fun containsSuspiciousPatterns(filename: String): Boolean {
        val suspiciousPatterns = listOf(
            "..", // Path traversal
            "/", "\\", // Path separators
            "<", ">", ":", "\"", "|", "?", "*", // Invalid filename characters
            "\u0000", // Null byte
            "CON", "PRN", "AUX", "NUL", // Windows reserved names
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        val upperFilename = filename.uppercase()
        return suspiciousPatterns.any { pattern ->
            upperFilename.contains(pattern.uppercase())
        }
    }

    private fun validateFileHeader(file: MultipartFile, extension: String): ValidationResult {
        try {
            val bytes = file.inputStream.use { it.readNBytes(16) }
            if (bytes.isEmpty()) {
                return ValidationResult(false, "Cannot read file header")
            }

            val isValid = when (extension.lowercase()) {
                "jpg", "jpeg" -> validateJpegHeader(bytes)
                "png" -> validatePngHeader(bytes)
                "gif" -> validateGifHeader(bytes)
                "pdf" -> validatePdfHeader(bytes)
                else -> true // Skip validation for unknown types
            }

            return if (isValid) {
                ValidationResult(true, "File header validation passed")
            } else {
                ValidationResult(false, "File header does not match expected format for .$extension file")
            }

        } catch (e: Exception) {
            logger.warn("Error validating file header", e)
            return ValidationResult(true, "Header validation skipped due to error") // Don't fail validation
        }
    }

    private fun validateJpegHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 3 &&
                bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
    }

    private fun validatePngHeader(bytes: ByteArray): Boolean {
        val pngSignature = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        )
        return bytes.size >= 8 && bytes.take(8).toByteArray().contentEquals(pngSignature)
    }

    private fun validateGifHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 6 &&
                (bytes.take(6).toByteArray().contentEquals("GIF87a".toByteArray()) ||
                        bytes.take(6).toByteArray().contentEquals("GIF89a".toByteArray()))
    }

    private fun validatePdfHeader(bytes: ByteArray): Boolean {
        return bytes.size >= 4 &&
                bytes.take(4).toByteArray().contentEquals("%PDF".toByteArray())
    }
}

/**
 * File validation result
 */
data class ValidationResult(
    val isValid: Boolean,
    val message: String
)