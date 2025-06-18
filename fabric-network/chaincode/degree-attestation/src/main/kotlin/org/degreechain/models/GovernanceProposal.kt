package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property

@DataType
data class GovernanceProposal(
    @Property
    @JsonProperty("proposalId")
    val proposalId: String,

    @Property
    @JsonProperty("proposerOrganization")
    val proposerOrganization: String,

    @Property
    @JsonProperty("title")
    val title: String,

    @Property
    @JsonProperty("description")
    val description: String,

    @Property
    @JsonProperty("proposalType")
    val proposalType: String, // NETWORK_PARAMETER, UNIVERSITY_ACTION, FEE_CHANGE

    @Property
    @JsonProperty("targetEntity")
    val targetEntity: String?, // University code if applicable

    @Property
    @JsonProperty("proposedChanges")
    val proposedChanges: Map<String, String>,

    @Property
    @JsonProperty("votingDeadline")
    val votingDeadline: String,

    @Property
    @JsonProperty("status")
    val status: String, // ACTIVE, PASSED, REJECTED, EXECUTED

    @Property
    @JsonProperty("votes")
    val votes: Map<String, String> = emptyMap(), // Organization -> APPROVE/REJECT

    @Property
    @JsonProperty("createdAt")
    val createdAt: String,

    @Property
    @JsonProperty("executedAt")
    val executedAt: String?
)