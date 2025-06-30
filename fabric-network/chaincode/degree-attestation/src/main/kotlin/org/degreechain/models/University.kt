package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property

@DataType
data class University(
    @Property
    @JsonProperty("universityCode")
    val universityCode: String,

    @Property
    @JsonProperty("universityName")
    val universityName: String,

    @Property
    @JsonProperty("country")
    val country: String,

    @Property
    @JsonProperty("address")
    val address: String,

    @Property
    @JsonProperty("contactEmail")
    val contactEmail: String,

    @Property
    @JsonProperty("publicKey")
    val publicKey: String,

    @Property
    @JsonProperty("stakeAmount")
    val stakeAmount: Double,

    @Property
    @JsonProperty("status")
    val status: String, // PENDING, ACTIVE, SUSPENDED, BLACKLISTED

    @Property
    @JsonProperty("accreditation")
    val accreditation: Map<String, String> = emptyMap(),

    @Property
    @JsonProperty("totalDegreesIssued")
    val totalDegreesIssued: Long = 0L,

    @Property
    @JsonProperty("revenue")
    val revenue: Double = 0.0,

    @Property
    @JsonProperty("joinedAt")
    val joinedAt: String,

    @Property
    @JsonProperty("lastActive")
    val lastActive: String,

    @Property
    @JsonProperty("version")
    val version: Long = 1L
)
