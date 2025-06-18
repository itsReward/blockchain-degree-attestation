package org.degreechain.common.exceptions

import org.degreechain.common.models.ErrorCode

class BlockchainException(
    override val message: String,
    val errorCode: ErrorCode = ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
    override val cause: Throwable? = null
) : TechnicalException(message, errorCode, cause) {

    val transactionId: String?
    val blockNumber: Long?
    val contractName: String?

    constructor(
        message: String,
        errorCode: ErrorCode = ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
        transactionId: String? = null,
        blockNumber: Long? = null,
        contractName: String? = null,
        cause: Throwable? = null
    ) : this(message, errorCode, cause) {
        this.transactionId = transactionId
        this.blockNumber = blockNumber
        this.contractName = contractName
    }

    init {
        this.transactionId = null
        this.blockNumber = null
        this.contractName = null
    }

    override fun toString(): String {
        return "BlockchainException(message='$message', errorCode=$errorCode, " +
                "transactionId=$transactionId, blockNumber=$blockNumber, contractName=$contractName)"
    }
}