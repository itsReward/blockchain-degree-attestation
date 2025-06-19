package org.degreechain.common.exceptions

import org.degreechain.common.models.ErrorCode

class BlockchainException(
    override val message: String,
    override val errorCode: ErrorCode = ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
    val transactionId: String? = null,
    val blockNumber: Long? = null,
    val contractName: String? = null,
    override val cause: Throwable? = null
) : TechnicalException(message, errorCode, cause) {

    // Alternative constructor for backward compatibility
    constructor(
        message: String,
        errorCode: ErrorCode = ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
        cause: Throwable? = null
    ) : this(message, errorCode, null, null, null, cause)

    override fun toString(): String {
        return "BlockchainException(message='$message', errorCode=$errorCode, " +
                "transactionId=$transactionId, blockNumber=$blockNumber, contractName=$contractName)"
    }
}