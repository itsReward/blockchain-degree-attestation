package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property
import java.time.LocalDateTime

@DataType
data class VerificationResultWithConfidence(
    @Property
    @JsonProperty("verified")
    val verified: Boolean,

    @Property
    @JsonProperty("degreeId")
    val degreeId: String?,

    @Property
    @JsonProperty("degree")
    val degree: DegreeWithHash?,

    @Property
    @JsonProperty("verificationMethod")
    val verificationMethod: String,

    @Property
    @JsonProperty("confidence")
    val confidence: Double,

    @Property
    @JsonProperty("message")
    val message: String,

    @Property
    @JsonProperty("timestamp")
    val timestamp: LocalDateTime
)