package org.degreechain.blockchain.models

import com.fasterxml.jackson.annotation.JsonProperty

data class BlockchainResponse(
    @JsonProperty("success")
    val success: Boolean,

    @JsonProperty("data")
    val data: String?,

    @JsonProperty("transactionId")
    val transactionId: String?,

    @JsonProperty("blockNumber")
    val blockNumber: Long?,

    @JsonProperty("timestamp")
    val timestamp: Long,

    @JsonProperty("error")
    val error: String? = null
)
