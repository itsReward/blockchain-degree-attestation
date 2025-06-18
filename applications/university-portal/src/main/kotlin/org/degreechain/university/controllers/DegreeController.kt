package org.degreechain.university.controllers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.common.models.ApiResponse
import org.degreechain.university.models.DegreeSubmission
import org.degreechain.university.models.BulkUpload
import org.degreechain.university.services.DegreeSubmissionService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/degrees")
@PreAuthorize("hasRole('UNIVERSITY')")
class DegreeController(
    private val degreeSubmissionService: DegreeSubmissionService
) {

    @PostMapping("/submit")
    suspend fun submitDegree(
        @Valid @RequestBody submission: DegreeSubmission
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = degreeSubmissionService.submitDegree(submission)
            ResponseEntity.ok(ApiResponse.success(result, "Degree submitted successfully"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to submit degree: ${submission.certificateNumber}" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @PostMapping("/bulk-upload")
    suspend fun bulkUploadDegrees(
        @RequestParam("file") file: MultipartFile,
        @RequestParam(required = false) batchSize: Int = 100
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val result = degreeSubmissionService.bulkUploadDegrees(file, batchSize)
            ResponseEntity.ok(ApiResponse.success(result, "Bulk upload completed"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to process bulk upload" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @GetMapping("/{certificateNumber}")
    suspend fun getDegree(
        @PathVariable certificateNumber: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val degree = degreeSubmissionService.getDegree(certificateNumber)
            ResponseEntity.ok(ApiResponse.success(degree, "Degree retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get degree: $certificateNumber" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @GetMapping
    suspend fun getUniversityDegrees(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val degrees = degreeSubmissionService.getUniversityDegrees(page, size, status)
            ResponseEntity.ok(ApiResponse.success(degrees, "Degrees retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get university degrees" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @PostMapping("/{certificateNumber}/revoke")
    suspend fun revokeDegree(
        @PathVariable certificateNumber: String,
        @RequestParam reason: String
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = degreeSubmissionService.revokeDegree(certificateNumber, reason)
            ResponseEntity.ok(ApiResponse.success(result, "Degree revoked"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to revoke degree: $certificateNumber" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping("/statistics")
    suspend fun getDegreeStatistics(): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val statistics = degreeSubmissionService.getDegreeStatistics()
            ResponseEntity.ok(ApiResponse.success(statistics, "Statistics retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get degree statistics" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }
}