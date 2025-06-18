package org.degreechain.authority.controllers

import mu.KotlinLogging
import org.degreechain.authority.models.GovernanceProposal
import org.degreechain.authority.services.GovernanceService
import org.degreechain.common.models.ApiResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import javax.validation.Valid

private val logger = KotlinLogging.logger {}

@RestController
@RequestMapping("/api/v1/governance")
@PreAuthorize("hasRole('ATTESTATION_AUTHORITY')")
class GovernanceController(
    private val governanceService: GovernanceService
) {

    @PostMapping("/proposals")
    suspend fun createProposal(
        @Valid @RequestBody proposal: GovernanceProposal
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = governanceService.createProposal(proposal)
            ResponseEntity.ok(ApiResponse.success(result, "Proposal created"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to create proposal" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @GetMapping("/proposals")
    suspend fun getAllProposals(): ResponseEntity<ApiResponse<List<Map<String, Any>>>> {
        return try {
            val proposals = governanceService.getAllProposals()
            ResponseEntity.ok(ApiResponse.success(proposals, "Proposals retrieved"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to get proposals" }
            ResponseEntity.badRequest().body(ApiResponse.error<List<Map<String, Any>>>(e))
        }
    }

    @PostMapping("/proposals/{proposalId}/vote")
    suspend fun vote(
        @PathVariable proposalId: String,
        @RequestParam vote: String,
        @RequestParam organization: String
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = governanceService.vote(proposalId, organization, vote)
            ResponseEntity.ok(ApiResponse.success(result, "Vote recorded"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to record vote for proposal: $proposalId" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }

    @PostMapping("/proposals/{proposalId}/execute")
    suspend fun executeProposal(
        @PathVariable proposalId: String
    ): ResponseEntity<ApiResponse<String>> {
        return try {
            val result = governanceService.executeProposal(proposalId)
            ResponseEntity.ok(ApiResponse.success(result, "Proposal executed"))
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute proposal: $proposalId" }
            ResponseEntity.badRequest().body(ApiResponse.error<String>(e))
        }
    }
}