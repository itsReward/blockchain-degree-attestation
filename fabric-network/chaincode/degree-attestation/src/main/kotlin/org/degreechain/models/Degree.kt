package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property
import java.util.Collections.emptyMap

@DataType
data class Degree(
    @Property
    @JsonProperty("certificateNumber")
    val certificateNumber: String,

    @Property
    @JsonProperty("universityCode")
    val universityCode: String,

    @Property
    @JsonProperty("studentName")
    val studentName: String,

    @Property
    @JsonProperty("degreeName")
    val degreeName: String,

    @Property
    @JsonProperty("facultyName")
    val facultyName: String,

    @Property
    @JsonProperty("degreeClassification")
    val degreeClassification: String,

    @Property
    @JsonProperty("issuanceDate")
    val issuanceDate: String,

    @Property
    @JsonProperty("expiryDate")
    val expiryDate: String?,

    @Property
    @JsonProperty("certificateHash")
    val certificateHash: String,

    @Property
    @JsonProperty("status")
    val status: String, // ACTIVE, REVOKED, EXPIRED

    @Property
    @JsonProperty("metadata")
    val metadata: Map<String, String> = emptyMap(),

    @Property
    @JsonProperty("createdAt")
    val createdAt: String,

    @Property
    @JsonProperty("updatedAt")
    val updatedAt: String,

    @Property
    @JsonProperty("version")
    val version: Long = 1L
)