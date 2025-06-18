package org.degreechain.blockchain.models

data class BlockchainResponse<T>(
    val success: Boolean,
    val data: T? = null,
    val transactionId: String? = null,
    val blockNumber: Long? = null,
    val timestamp: Long,
    val error: String? = null
)