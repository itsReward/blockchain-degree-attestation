package org.degreechain.models

import com.fasterxml.jackson.annotation.JsonProperty
import org.hyperledger.fabric.contract.annotation.DataType
import org.hyperledger.fabric.contract.annotation.Property

@DataType
data class Payment(
    @Property
    @JsonProperty("paymentId")
    val paymentId: String,

    @Property
    @JsonProperty("fromOrganization")
    val fromOrganization: String,

    @Property
    @JsonProperty("toOrganization")
    val toOrganization: String,

    @Property
    @JsonProperty("amount")
    val amount: Double,

    @Property
    @JsonProperty("currency")
    val currency: String,

    @Property
    @JsonProperty("paymentType")
    val paymentType: String, // VERIFICATION_FEE, STAKE, REVENUE_SHARE, REFUND

    @Property
    @JsonProperty("status")
    val status: String, // PENDING, COMPLETED, FAILED, REFUNDED

    @Property
    @JsonProperty("transactionHash")
    val transactionHash: String?,

    @Property
    @JsonProperty("relatedEntityId")
    val relatedEntityId: String?, // Certificate number or verification ID

    @Property
    @JsonProperty("timestamp")
    val timestamp: String,

    @Property
    @JsonProperty("metadata")
    val metadata: Map<String, String> = emptyMap()
)