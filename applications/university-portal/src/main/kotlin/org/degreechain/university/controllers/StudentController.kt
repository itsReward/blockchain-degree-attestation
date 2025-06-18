package org.degreechain.university.controllers

import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.university.models.StudentRecord
import org.degreechain.university.services.StudentDataService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/students")
@PreAuthorize("hasRole('UNIVERSITY')")
class StudentController(
    private val studentDataService: StudentDataService
) {

    @PostMapping
    suspend fun createStudentRecord(
        @Valid @RequestBody studentRecord: StudentRecord
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = studentDataService.createStudentRecord(studentRecord)
            ResponseEntity.ok(ApiResponse.success(result, "Student record created"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to create student record" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping("/{studentId}")
    suspend fun getStudentRecord(
        @PathVariable studentId: String
    ): ResponseEntity<ApiResponse<StudentRecord>> {
        return try {
            val student = studentDataService.getStudentRecord(studentId)
            ResponseEntity.ok(ApiResponse.success(student, "Student record retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get student record: $studentId" }
            ResponseEntity.badRequest().body(ApiResponse.error<StudentRecord>(e))
        }
    }

    @PutMapping("/{studentId}")
    suspend fun updateStudentRecord(
        @PathVariable studentId: String,
        @Valid @RequestBody studentRecord: StudentRecord
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = studentDataService.updateStudentRecord(studentId, studentRecord)
            ResponseEntity.ok(ApiResponse.success(result, "Student record updated"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to update student record: $studentId" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping
    suspend fun searchStudents(
        @RequestParam(required = false) query: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val students = studentDataService.searchStudents(query, page, size)
            ResponseEntity.ok(ApiResponse.success(students, "Students retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to search students" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @GetMapping("/{studentId}/degrees")
    suspend fun getStudentDegrees(
        @PathVariable studentId: String
    ): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        return try {
            val degrees = studentDataService.getStudentDegrees(studentId)
            ResponseEntity.ok(ApiResponse.success(degrees, "Student degrees retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get student degrees: $studentId" }
            ResponseEntity.badRequest().body(ApiResponse.error<List<Map<String, Any>>>(e))
        }
    }
}