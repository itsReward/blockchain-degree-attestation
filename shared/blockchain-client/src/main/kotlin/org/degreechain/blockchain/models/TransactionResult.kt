package org.degreechain.blockchain.models

data class TransactionResult(
    val transactionId: String,
    val result: String,
    val success: Boolean,
    val blockNumber: Long? = null,
    val timestamp: Long,
    val gasUsed: Long? = null,
    val error: String? = null
)