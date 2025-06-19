// shared/blockchain-client/src/main/kotlin/org/degreechain/blockchain/FabricGatewayClient.kt
package org.degreechain.blockchain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.degreechain.blockchain.config.FabricConfig
import org.degreechain.blockchain.models.BlockchainResponse
import org.degreechain.blockchain.models.TransactionResult
import org.degreechain.common.exceptions.BlockchainException
import org.degreechain.common.models.ErrorCode
import org.hyperledger.fabric.gateway.*
import java.nio.file.Paths
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

class FabricGatewayClient(
    private val config: FabricConfig
) {
    private var gateway: Gateway? = null
    private var network: Network? = null
    private var contract: Contract? = null

    suspend fun initialize(): FabricGatewayClient = withContext(Dispatchers.IO) {
        try {
            logger.info { "Initializing Fabric Gateway Client for ${config.organizationName}" }

            // Load wallet
            val wallet = Wallets.newFileSystemWallet(Paths.get(config.walletPath))

            // Check if identity exists in wallet (fixed method call)
            val identity = wallet.get(config.userId)
            if (identity == null) {
                throw BlockchainException(
                    "Identity ${config.userId} not found in wallet",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
                )
            }

            // Create gateway builder
            val builder = Gateway.createBuilder()
                .identity(wallet, config.userId)
                .networkConfig(Paths.get(config.networkConfigPath))
                .discovery(config.discoveryEnabled)

            // Build gateway
            gateway = builder.connect()

            // Get network
            network = gateway!!.getNetwork(config.channelName)

            // Get contract
            contract = network!!.getContract(config.contractName)

            logger.info { "Fabric Gateway Client initialized successfully" }
            this@FabricGatewayClient
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Fabric Gateway Client" }
            throw BlockchainException(
                "Failed to initialize blockchain connection: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun submitTransaction(
        functionName: String,
        vararg args: String
    ): TransactionResult = withContext(Dispatchers.IO) {
        try {
            logger.debug { "Submitting transaction: $functionName with args: ${args.contentToString()}" }

            val transaction = contract?.createTransaction(functionName)
                ?: throw BlockchainException(
                    "Contract not initialized",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
                )

            val result = transaction.submit(*args)
            val resultString = String(result, Charsets.UTF_8)

            logger.debug { "Transaction $functionName completed successfully" }

            TransactionResult(
                transactionId = transaction.transactionId,
                result = resultString,
                success = true,
                blockNumber = null,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error { "Transaction $functionName failed: ${e.message}" }
            throw BlockchainException(
                "Transaction failed: ${e.message}",
                ErrorCode.CHAINCODE_EXECUTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun evaluateTransaction(
        functionName: String,
        vararg args: String
    ): BlockchainResponse<String> = withContext(Dispatchers.IO) {
        try {
            logger.debug { "Evaluating transaction: $functionName with args: ${args.contentToString()}" }

            val result = contract?.evaluateTransaction(functionName, *args)
                ?: throw BlockchainException(
                    "Contract not initialized",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
                )

            val resultString = String(result, Charsets.UTF_8)

            logger.debug { "Transaction $functionName evaluated successfully" }

            BlockchainResponse(
                success = true,
                data = resultString,
                transactionId = null,
                blockNumber = null,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error { "Evaluation $functionName failed: ${e.message}" }
            throw BlockchainException(
                "Transaction evaluation failed: ${e.message}",
                ErrorCode.CHAINCODE_EXECUTION_ERROR,
                cause = e
            )
        }
    }

    suspend fun submitTransactionWithTimeout(
        functionName: String,
        timeoutSeconds: Long,
        vararg args: String
    ): TransactionResult = withContext(Dispatchers.IO) {
        try {
            logger.debug { "Submitting transaction with timeout: $functionName" }

            val transaction = contract?.createTransaction(functionName)
                ?: throw BlockchainException(
                    "Contract not initialized",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
                )

            val result = transaction.submit(*args)
            val resultString = String(result, Charsets.UTF_8)

            TransactionResult(
                transactionId = transaction.transactionId,
                result = resultString,
                success = true,
                blockNumber = null,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            when (e.message?.contains("timeout", ignoreCase = true)) {
                true -> {
                    logger.error { "Transaction $functionName timed out" }
                    throw BlockchainException(
                        "Transaction timed out after ${timeoutSeconds}s",
                        ErrorCode.TRANSACTION_TIMEOUT,
                        cause = e
                    )
                }
                else -> {
                    logger.error { "Transaction $functionName failed: ${e.message}" }
                    throw BlockchainException(
                        "Transaction failed: ${e.message}",
                        ErrorCode.CHAINCODE_EXECUTION_ERROR,
                        cause = e
                    )
                }
            }
        }
    }

    fun close() {
        try {
            gateway?.close()
            logger.info { "Fabric Gateway Client closed" }
        } catch (e: Exception) {
            logger.warn { "Error closing Fabric Gateway Client: ${e.message}" }
        }
    }
}