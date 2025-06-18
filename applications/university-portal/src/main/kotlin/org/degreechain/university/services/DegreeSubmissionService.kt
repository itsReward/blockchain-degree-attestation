// applications/university-portal/src/main/kotlin/org/degreechain/university/services/DegreeSubmissionService.kt
package org.degreechain.university.services

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.utils.CryptoUtils
import org.degreechain.common.utils.ValidationUtils
import org.degreechain.university.config.UniversityConfig
import org.degreechain.university.models.DegreeSubmission
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

@Service
class DegreeSubmissionService(
    private val contractInvoker: ContractInvoker,
    private val studentDataService: StudentDataService,
    private val universityConfig: UniversityConfig
) {
    private val objectMapper = ObjectMapper().registerKotlinModule()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun submitDegree(submission: DegreeSubmission): String = withContext(Dispatchers.IO) {
        logger.info { "Submitting degree: ${submission.certificateNumber}" }

        // Validate submission
        validateDegreeSubmission(submission)

        // Verify student exists
        studentDataService.getStudentRecord(submission.studentId)

        // Generate certificate hash
        val certificateHash = generateCertificateHash(submission)

        // Submit to blockchain
        val result = contractInvoker.submitDegree(
            certificateNumber = submission.certificateNumber,
            studentName = submission.studentName,
            degreeName = submission.degreeName,
            facultyName = submission.facultyName,
            degreeClassification = submission.degreeClassification,
            issuanceDate = submission.issuanceDate.format(dateFormatter),
            expiryDate = submission.expiryDate?.format(dateFormatter),
            certificateHash = certificateHash
        )

        logger.info { "Degree submitted successfully: ${submission.certificateNumber}" }
        result
    }

    suspend fun bulkUploadDegrees(file: MultipartFile, batchSize: Int): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.info { "Processing bulk upload with batch size: $batchSize" }

        // Validate file
        if (file.isEmpty) {
            throw BusinessException("File is empty", ErrorCode.VALIDATION_ERROR)
        }

        val contentType = file.contentType
        if (contentType != "text/csv" && contentType != "application/vnd.ms-excel") {
            throw BusinessException("Invalid file type. Only CSV files are supported", ErrorCode.VALIDATION_ERROR)
        }

        // Parse CSV file
        val degrees = parseCsvFile(file)
        logger.info { "Parsed ${degrees.size} degrees from CSV" }

        var successCount = 0
        var errorCount = 0
        val errors = mutableListOf<String>()

        // Process in batches
        degrees.chunked(batchSize).forEach { batch ->
            val batchResults = batch.map { degree ->
                async {
                    try {
                        submitDegree(degree)
                        successCount++
                        null
                    } catch (e: Exception) {
                        errorCount++
                        val error = "Certificate ${degree.certificateNumber}: ${e.message}"
                        errors.add(error)
                        logger.warn { error }
                        error
                    }
                }
            }

            batchResults.awaitAll()
        }

        val result = mapOf(
            "totalProcessed" to degrees.size,
            "successful" to successCount,
            "failed" to errorCount,
            "errors" to errors.take(10), // Limit errors to first 10
            "batchSize" to batchSize
        )

        logger.info { "Bulk upload completed: $result" }
        result
    }

    suspend fun getDegree(certificateNumber: String): Map<String, Any> = withContext(Dispatchers.IO) {
        try {
            val degreeJson = contractInvoker.getDegree(certificateNumber)
            val degree: Map<String, Any> = objectMapper.readValue(degreeJson)

            // Check if this university owns the degree
            if (degree["universityCode"] != universityConfig.universityCode) {
                throw BusinessException("Access denied: Degree not issued by your university", ErrorCode.FORBIDDEN_OPERATION)
            }

            degree
        } catch (e: Exception) {
            if (e is BusinessException) throw e
            throw BusinessException("Degree not found: $certificateNumber", ErrorCode.RESOURCE_NOT_FOUND, cause = e)
        }
    }

    suspend fun getUniversityDegrees(page: Int, size: Int, status: String?): Map<String, Any> = withContext(Dispatchers.IO) {
        // In a real implementation, this would query the blockchain with filters
        // For now, we'll simulate pagination

        val allDegrees = getAllUniversityDegrees()
        val filteredDegrees = if (status != null) {
            allDegrees.filter { it["status"] == status }
        } else {
            allDegrees
        }

        val startIndex = page * size
        val endIndex = minOf(startIndex + size, filteredDegrees.size)
        val paginatedDegrees = if (startIndex < filteredDegrees.size) {
            filteredDegrees.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        mapOf(
            "degrees" to paginatedDegrees,
            "page" to page,
            "size" to size,
            "totalElements" to filteredDegrees.size,
            "totalPages" to (filteredDegrees.size + size - 1) / size,
            "hasNext" to (endIndex < filteredDegrees.size),
            "hasPrevious" to (page > 0)
        )
    }

    suspend fun revokeDegree(certificateNumber: String, reason: String): String = withContext(Dispatchers.IO) {
        logger.warn { "Revoking degree: $certificateNumber, reason: $reason" }

        // Verify degree exists and belongs to this university
        val degree = getDegree(certificateNumber)

        // In a real implementation, this would update the degree status on blockchain
        // For now, we'll simulate the revocation
        "Degree $certificateNumber revoked. Reason: $reason"
    }

    suspend fun getDegreeStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        // In a real implementation, this would aggregate data from blockchain
        val allDegrees = getAllUniversityDegrees()

        val statusCounts = allDegrees.groupBy { it["status"] }.mapValues { it.value.size }
        val degreeTypeCounts = allDegrees.groupBy { it["degreeName"] }.mapValues { it.value.size }

        val currentYear = LocalDateTime.now().year
        val currentYearDegrees = allDegrees.filter { degree ->
            val issuanceDate = degree["issuanceDate"] as? String
            issuanceDate?.startsWith(currentYear.toString()) == true
        }

        mapOf(
            "totalDegrees" to allDegrees.size,
            "statusBreakdown" to statusCounts,
            "degreeTypeBreakdown" to degreeTypeCounts,
            "degreesThisYear" to currentYearDegrees.size,
            "universityCode" to universityConfig.universityCode
        )
    }

    private suspend fun getAllUniversityDegrees(): List<Map<String, Any>> {
        // In a real implementation, this would query blockchain for all degrees from this university
        // For now, return empty list as placeholder
        return emptyList()
    }

    private fun validateDegreeSubmission(submission: DegreeSubmission) {
        ValidationUtils.validateCertificateNumber(submission.certificateNumber)
        ValidationUtils.validateRequired(submission.studentName, "Student Name")
        ValidationUtils.validateRequired(submission.degreeName, "Degree Name")
        ValidationUtils.validateRequired(submission.facultyName, "Faculty Name")
        ValidationUtils.validateRequired(submission.degreeClassification, "Degree Classification")
        ValidationUtils.validateRequired(submission.studentId, "Student ID")

        // Validate dates
        if (submission.expiryDate != null && submission.expiryDate.isBefore(submission.issuanceDate)) {
            throw BusinessException("Expiry date cannot be before issuance date", ErrorCode.VALIDATION_ERROR)
        }
    }

    private fun generateCertificateHash(submission: DegreeSubmission): String {
        val certificateData = "${submission.certificateNumber}${submission.studentName}" +
                "${submission.degreeName}${submission.facultyName}${submission.degreeClassification}" +
                "${submission.issuanceDate}${universityConfig.universityCode}"

        return CryptoUtils.generateSHA256Hash(certificateData)
    }

    private fun parseCsvFile(file: MultipartFile): List<DegreeSubmission> {
        val csvContent = String(file.bytes)
        val lines = csvContent.split("\n").filter { it.isNotBlank() }

        if (lines.isEmpty()) {
            throw BusinessException("CSV file is empty", ErrorCode.VALIDATION_ERROR)
        }

        val headers = lines[0].split(",").map { it.trim() }
        val requiredHeaders = listOf("certificateNumber", "studentId", "studentName", "degreeName", "facultyName", "degreeClassification", "issuanceDate")

        val missingHeaders = requiredHeaders.filter { it !in headers }
        if (missingHeaders.isNotEmpty()) {
            throw BusinessException("Missing required CSV headers: ${missingHeaders.joinToString()}", ErrorCode.VALIDATION_ERROR)
        }

        return lines.drop(1).mapIndexed { index, line ->
            try {
                val values = line.split(",").map { it.trim() }
                val row = headers.zip(values).toMap()

                DegreeSubmission(
                    certificateNumber = row["certificateNumber"] ?: throw BusinessException("Missing certificate number", ErrorCode.VALIDATION_ERROR),
                    studentId = row["studentId"] ?: throw BusinessException("Missing student ID", ErrorCode.VALIDATION_ERROR),
                    studentName = row["studentName"] ?: throw BusinessException("Missing student name", ErrorCode.VALIDATION_ERROR),
                    degreeName = row["degreeName"] ?: throw BusinessException("Missing degree name", ErrorCode.VALIDATION_ERROR),
                    facultyName = row["facultyName"] ?: throw BusinessException("Missing faculty name", ErrorCode.VALIDATION_ERROR),
                    degreeClassification = row["degreeClassification"] ?: throw BusinessException("Missing degree classification", ErrorCode.VALIDATION_ERROR),
                    issuanceDate = LocalDateTime.parse(row["issuanceDate"], dateFormatter),
                    expiryDate = row["expiryDate"]?.let { if (it.isNotBlank()) LocalDateTime.parse(it, dateFormatter) else null },
                    metadata = emptyMap()
                )
            } catch (e: Exception) {
                throw BusinessException("Error parsing CSV line ${index + 2}: ${e.message}", ErrorCode.VALIDATION_ERROR, cause = e)
            }
        }
    }
}