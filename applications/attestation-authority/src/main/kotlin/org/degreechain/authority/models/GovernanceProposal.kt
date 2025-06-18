package org.degreechain.authority.models

import org.degreechain.common.models.BaseEntity
import java.time.LocalDateTime
import javax.validation.constraints.*

data class GovernanceProposal(
    @field:NotBlank(message = "Proposer organization is required")
    val proposerOrganization: String,

    @field:NotBlank(message = "Title is required")
    @field:Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    val title: String,

    @field:NotBlank(message = "Description is required")
    @field:Size(min = 10, max = 2000, message = "Description must be between 10 and 2000 characters")
    val description: String,

    @field:NotBlank(message = "Proposal type is required")
    @field:Pattern(
        regexp = "^(NETWORK_PARAMETER|UNIVERSITY_ACTION|FEE_CHANGE)$",
        message = "Invalid proposal type"
    )
    val proposalType: String,

    val targetEntity: String? = null,
    val proposedChanges: Map<String, String> = emptyMap(),

    @field:Future(message = "Voting deadline must be in the future")
    val votingDeadline: LocalDateTime,

    val metadata: Map<String, String> = emptyMap()
) : BaseEntity()