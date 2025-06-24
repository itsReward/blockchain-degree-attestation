package org.degreechain.blockchain.models

data class BlockchainResponse<T>(
    val success: Boolean,
    val data: T?,
    val transactionId: String? = null,
    val blockNumber: Long? = null,
    val timestamp: Long,
    val error: String? = null
)

data class TransactionResult(
    val transactionId: String,
    val result: String,
    val success: Boolean,
    val blockNumber: Long?,
    val timestamp: Long,
    val error: String? = null
)
