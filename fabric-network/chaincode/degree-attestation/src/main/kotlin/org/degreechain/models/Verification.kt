package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property

@DataType
data class Verification(
    @Property
    @JsonProperty("verificationId")
    val verificationId: String,

    @Property
    @JsonProperty("certificateNumber")
    val certificateNumber: String,

    @Property
    @JsonProperty("verifierOrganization")
    val verifierOrganization: String,

    @Property
    @JsonProperty("verifierEmail")
    val verifierEmail: String,

    @Property
    @JsonProperty("verificationTimestamp")
    val verificationTimestamp: String,

    @Property
    @JsonProperty("verificationResult")
    val verificationResult: String, // VERIFIED, FAILED, EXPIRED

    @Property
    @JsonProperty("confidence")
    val confidence: Double,

    @Property
    @JsonProperty("paymentId")
    val paymentId: String,

    @Property
    @JsonProperty("verificationFee")
    val verificationFee: Double,

    @Property
    @JsonProperty("extractionMethod")
    val extractionMethod: String, // HASH, OCR, HYBRID

    @Property
    @JsonProperty("metadata")
    val metadata: Map<String, String> = emptyMap()
)