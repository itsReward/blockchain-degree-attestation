package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property
import java.time.LocalDateTime

@DataType
data class DegreeWithHash(
    @Property
    @JsonProperty("degreeId")
    val degreeId: String,

    @Property
    @JsonProperty("studentId")
    val studentId: String,

    @Property
    @JsonProperty("degreeName")
    val degreeName: String,

    @Property
    @JsonProperty("institutionName")
    val institutionName: String,

    @Property
    @JsonProperty("issuanceDate")
    val issuanceDate: LocalDateTime,

    @Property
    @JsonProperty("certificateHash")
    val certificateHash: String,

    @Property
    @JsonProperty("ocrData")
    val ocrData: String,

    @Property
    @JsonProperty("processedImageUrl")
    val processedImageUrl: String,

    @Property
    @JsonProperty("submissionDate")
    val submissionDate: LocalDateTime,

    @Property
    @JsonProperty("status")
    val status: DegreeStatus,

    @Property
    @JsonProperty("verificationCount")
    val verificationCount: Int,

    @Property
    @JsonProperty("lastVerified")
    val lastVerified: LocalDateTime?
)