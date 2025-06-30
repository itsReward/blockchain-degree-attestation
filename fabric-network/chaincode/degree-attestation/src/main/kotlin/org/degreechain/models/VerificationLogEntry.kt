package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property
import java.time.LocalDateTime

@DataType
data class VerificationLogEntry(
    @Property
    @JsonProperty("verificationId")
    val verificationId: String,

    @Property
    @JsonProperty("degreeId")
    val degreeId: String,

    @Property
    @JsonProperty("verifierOrg")
    val verifierOrg: String,

    @Property
    @JsonProperty("verificationMethod")
    val verificationMethod: String,

    @Property
    @JsonProperty("confidence")
    val confidence: Double,

    @Property
    @JsonProperty("timestamp")
    val timestamp: LocalDateTime,

    @Property
    @JsonProperty("extractedHash")
    val extractedHash: String
)