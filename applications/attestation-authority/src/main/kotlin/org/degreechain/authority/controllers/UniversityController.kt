package org.degreechain.authority.controllers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.authority.models.UniversityRegistration
import org.degreechain.authority.services.UniversityService
import org.degreechain.common.models.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/universities")
@PreAuthorize("hasRole('ATTESTATION_AUTHORITY')")
class UniversityController(
    private val universityService: UniversityService
) {

    @PostMapping("/enroll")
    suspend fun enrollUniversity(
        @Valid @RequestBody registration: UniversityRegistration
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val statistics = universityService.getUniversityStatistics(universityCode)
            ResponseEntity.ok(ApiResponse.success(statistics, "University statistics retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get university statistics: $universityCode" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }

    @GetMapping("/pending")
    suspend fun getPendingUniversities(): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        return try {
            val pending = universityService.getPendingUniversities()
            ResponseEntity.ok(ApiResponse.success(pending, "Pending universities retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get pending universities" }
            ResponseEntity.badRequest().body(ApiResponse.error<List<Map<String, Any>>>(e))
        }
    }

    @PostMapping("/{universityCode}/approve")
    suspend fun approveUniversity(
        @PathVariable universityCode: String
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = universityService.approveUniversity(universityCode)
            ResponseEntity.ok(ApiResponse.success(result, "University approved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to approve university: $universityCode" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @PostMapping("/{universityCode}/blacklist")
    suspend fun blacklistUniversity(
        @PathVariable universityCode: String,
        @RequestParam reason: String
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = universityService.blacklistUniversity(universityCode, reason)
            ResponseEntity.ok(ApiResponse.success(result, "University blacklisted"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to blacklist university: $universityCode" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping
    suspend fun getAllUniversities(): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        return try {
            val universities = universityService.getAllUniversities()
            ResponseEntity.ok(ApiResponse.success(universities, "Universities retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get universities" }
            ResponseEntity.badRequest().body(ApiResponse.error<List<Map<String, Any>>>(e))
        }
    }

    @GetMapping("/{universityCode}")
    suspend fun getUniversity(
        @PathVariable universityCode: String
    ): ResponseEntity<ApiResponse<Map<String, Any>>> {
        return try {
            val university = universityService.getUniversity(universityCode)
            ResponseEntity.ok(ApiResponse.success(university, "University retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get university: $universityCode" }
            ResponseEntity.badRequest().body(ApiResponse.error<Map<String, Any>>(e))
        }
    }
}