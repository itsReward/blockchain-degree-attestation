package org.degreechain.university.services

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.common.exceptions.BusinessException
import org.degreechain.common.models.ErrorCode
import org.degreechain.common.utils.ValidationUtils
import org.degreechain.university.config.UniversityConfig
import org.degreechain.university.models.StudentRecord
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

@Service
class StudentDataService(
    private val universityConfig: UniversityConfig
) {
    // In-memory storage for demo purposes - in production this would be a database
    private val studentRecords = ConcurrentHashMap<String, StudentRecord>()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    suspend fun createStudentRecord(studentRecord: StudentRecord): String = withContext(Dispatchers.IO) {
        logger.info { "Creating student record for: ${studentRecord.studentId}" }

        // Validate student record
        validateStudentRecord(studentRecord)

        // Check if student already exists
        if (studentRecords.containsKey(studentRecord.studentId)) {
            throw BusinessException(
                "Student record already exists: ${studentRecord.studentId}",
                ErrorCode.VALIDATION_ERROR
            )
        }

        // Store student record
        val recordWithTimestamp = studentRecord.copy(
            createdAt = LocalDateTime.now(),
            updatedAt = LocalDateTime.now()
        )

        studentRecords[studentRecord.studentId] = recordWithTimestamp

        logger.info { "Student record created successfully: ${studentRecord.studentId}" }
        "Student record created successfully for ${studentRecord.studentId}"
    }

    suspend fun getStudentRecord(studentId: String): StudentRecord = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving student record: $studentId" }

        ValidationUtils.validateRequired(studentId, "Student ID")

        val record = studentRecords[studentId]
            ?: throw BusinessException(
                "Student record not found: $studentId",
                ErrorCode.RESOURCE_NOT_FOUND
            )

        record
    }

    suspend fun updateStudentRecord(studentId: String, studentRecord: StudentRecord): String = withContext(Dispatchers.IO) {
        logger.info { "Updating student record: $studentId" }

        // Validate student record
        validateStudentRecord(studentRecord)

        // Check if student exists
        val existingRecord = studentRecords[studentId]
            ?: throw BusinessException(
                "Student record not found: $studentId",
                ErrorCode.RESOURCE_NOT_FOUND
            )

        // Update student record
        val updatedRecord = studentRecord.copy(
            studentId = studentId, // Ensure student ID doesn't change
            createdAt = existingRecord.createdAt,
            updatedAt = LocalDateTime.now()
        )

        studentRecords[studentId] = updatedRecord

        logger.info { "Student record updated successfully: $studentId" }
        "Student record updated successfully for $studentId"
    }

    suspend fun searchStudents(
        query: String?,
        page: Int,
        size: Int
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Searching students - query: $query, page: $page, size: $size" }

        val allStudents = studentRecords.values.toList()

        val filteredStudents = if (query.isNullOrBlank()) {
            allStudents
        } else {
            val queryLower = query.lowercase()
            allStudents.filter { student ->
                student.firstName.lowercase().contains(queryLower) ||
                        student.lastName.lowercase().contains(queryLower) ||
                        student.studentId.lowercase().contains(queryLower) ||
                        (student.email?.lowercase()?.contains(queryLower) == true)
            }
        }

        // Sort by last name, first name
        val sortedStudents = filteredStudents.sortedWith(
            compareBy<StudentRecord> { it.lastName }
                .thenBy { it.firstName }
        )

        // Apply pagination
        val startIndex = page * size
        val endIndex = minOf(startIndex + size, sortedStudents.size)
        val paginatedStudents = if (startIndex < sortedStudents.size) {
            sortedStudents.subList(startIndex, endIndex)
        } else {
            emptyList()
        }

        mapOf(
            "students" to paginatedStudents,
            "page" to page,
            "size" to size,
            "totalElements" to filteredStudents.size,
            "totalPages" to (filteredStudents.size + size - 1) / size,
            "hasNext" to (endIndex < filteredStudents.size),
            "hasPrevious" to (page > 0),
            "query" to query
        )
    }

    suspend fun getStudentDegrees(studentId: String): List<Map<String, Any>> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving degrees for student: $studentId" }

        // Verify student exists
        getStudentRecord(studentId)

        // In a real implementation, this would query the blockchain for degrees issued to this student
        // For now, return empty list as placeholder
        // This would involve querying the blockchain for degrees where the student matches

        // Mock implementation
        emptyList()
    }

    suspend fun deleteStudentRecord(studentId: String): String = withContext(Dispatchers.IO) {
        logger.warn { "Deleting student record: $studentId" }

        if (!studentRecords.containsKey(studentId)) {
            throw BusinessException(
                "Student record not found: $studentId",
                ErrorCode.RESOURCE_NOT_FOUND
            )
        }

        studentRecords.remove(studentId)

        logger.warn { "Student record deleted: $studentId" }
        "Student record deleted successfully for $studentId"
    }

    suspend fun getStudentStatistics(): Map<String, Any> = withContext(Dispatchers.IO) {
        logger.debug { "Retrieving student statistics" }

        val allStudents = studentRecords.values
        val currentYear = LocalDateTime.now().year

        val studentsThisYear = allStudents.count { student ->
            student.createdAt.year == currentYear
        }

        val programBreakdown = allStudents
            .groupBy { it.program }
            .mapValues { it.value.size }

        val statusBreakdown = allStudents
            .groupBy { it.status }
            .mapValues { it.value.size }

        mapOf(
            "totalStudents" to allStudents.size,
            "studentsThisYear" to studentsThisYear,
            "programBreakdown" to programBreakdown,
            "statusBreakdown" to statusBreakdown,
            "lastUpdated" to LocalDateTime.now().format(dateFormatter)
        )
    }

    private fun validateStudentRecord(studentRecord: StudentRecord) {
        ValidationUtils.validateRequired(studentRecord.studentId, "Student ID")
        ValidationUtils.validateRequired(studentRecord.firstName, "First Name")
        ValidationUtils.validateRequired(studentRecord.lastName, "Last Name")
        ValidationUtils.validateRequired(studentRecord.program, "Program")
        ValidationUtils.validateRequired(studentRecord.status, "Status")

        if (studentRecord.email != null) {
            ValidationUtils.validateEmail(studentRecord.email)
        }

        // Validate status values
        val validStatuses = setOf("ACTIVE", "GRADUATED", "SUSPENDED", "WITHDRAWN")
        if (studentRecord.status !in validStatuses) {
            throw BusinessException(
                "Invalid student status. Must be one of: ${validStatuses.joinToString()}",
                ErrorCode.VALIDATION_ERROR
            )
        }

        // Validate year level if provided
        if (studentRecord.yearLevel != null && (studentRecord.yearLevel < 1 || studentRecord.yearLevel > 8)) {
            throw BusinessException(
                "Year level must be between 1 and 8",
                ErrorCode.VALIDATION_ERROR
            )
        }
    }
}