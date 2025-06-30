package org.degreechain.blockchain.models

import com.fasterxml.jackson.annotation.JsonProperty

data class TransactionResult(
    @JsonProperty("success")
    val success: Boolean,

    @JsonProperty("transactionId")
    val transactionId: String?,

    @JsonProperty("result")
    val result: String?,

    @JsonProperty("error")
    val error: String?,

    @JsonProperty("blockNumber")
    val blockNumber: Long?,

    @JsonProperty("timestamp")
    val timestamp: Long
)