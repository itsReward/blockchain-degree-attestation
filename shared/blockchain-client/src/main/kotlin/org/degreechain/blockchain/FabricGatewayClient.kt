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
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeoutException

private val logger = KotlinLogging.logger {}

open class FabricGatewayClient(
    private val config: FabricConfig?
) {
    private var gateway: Gateway? = null
    private var network: Network? = null
    private var contract: Contract? = null
    private var isInitialized = false

    open suspend fun initialize(): FabricGatewayClient = withContext(Dispatchers.IO) {
        try {
            logger.info { "Initializing Fabric Gateway Client for ${config?.organizationName}" }

            if (config == null) {
                throw BlockchainException(
                    "Fabric configuration is null",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
                )
            }

            // Validate configuration paths
            validateConfiguration()

            // Load wallet
            val wallet = Wallets.newFileSystemWallet(Paths.get(config.walletPath))

            // Check if identity exists in wallet
            val identity = wallet.get(config.userId)
            if (identity == null) {
                throw BlockchainException(
                    "Identity ${config.userId} not found in wallet at ${config.walletPath}",
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

            isInitialized = true
            logger.info { "Fabric Gateway Client initialized successfully" }
            this@FabricGatewayClient
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize Fabric Gateway Client" }
            isInitialized = false
            throw BlockchainException(
                "Failed to initialize blockchain connection: ${e.message}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                cause = e
            )
        }
    }

    private fun validateConfiguration() {
        if (config == null) return

        // Check if network config file exists
        if (config.networkConfigPath.isNotEmpty() && !Files.exists(Paths.get(config.networkConfigPath))) {
            throw BlockchainException(
                "Network configuration file not found: ${config.networkConfigPath}",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
            )
        }

        // Check if wallet directory exists
        if (config.walletPath.isNotEmpty() && !Files.exists(Paths.get(config.walletPath))) {
            logger.warn { "Wallet directory does not exist, creating: ${config.walletPath}" }
            try {
                Files.createDirectories(Paths.get(config.walletPath))
            } catch (e: Exception) {
                throw BlockchainException(
                    "Failed to create wallet directory: ${config.walletPath}",
                    ErrorCode.BLOCKCHAIN_CONNECTION_ERROR,
                    cause = e
                )
            }
        }
    }

    open suspend fun submitTransaction(
        functionName: String,
        vararg args: String
    ): TransactionResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
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
                blockNumber = null, // Fabric Gateway doesn't provide block number directly
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            logger.error(e) { "Transaction $functionName failed: ${e.message}" }
            when {
                e.message?.contains("timeout", ignoreCase = true) == true -> {
                    throw BlockchainException(
                        "Transaction timed out: ${e.message}",
                        ErrorCode.TRANSACTION_TIMEOUT,
                        cause = e
                    )
                }
                e.message?.contains("endorsement", ignoreCase = true) == true -> {
                    throw BlockchainException(
                        "Transaction endorsement failed: ${e.message}",
                        ErrorCode.CHAINCODE_EXECUTION_ERROR,
                        cause = e
                    )
                }
                else -> {
                    throw BlockchainException(
                        "Transaction failed: ${e.message}",
                        ErrorCode.CHAINCODE_EXECUTION_ERROR,
                        cause = e
                    )
                }
            }
        }
    }

    open suspend fun evaluateTransaction(
        functionName: String,
        vararg args: String
    ): BlockchainResponse<String> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
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
            logger.error(e) { "Evaluation $functionName failed: ${e.message}" }
            throw BlockchainException(
                "Transaction evaluation failed: ${e.message}",
                ErrorCode.CHAINCODE_EXECUTION_ERROR,
                cause = e
            )
        }
    }

    open suspend fun submitTransactionWithTimeout(
        functionName: String,
        timeoutSeconds: Long,
        vararg args: String
    ): TransactionResult = submitTransaction(functionName, *args)

    private fun ensureInitialized() {
        if (!isInitialized || gateway == null || network == null || contract == null) {
            throw BlockchainException(
                "Fabric Gateway Client not initialized. Call initialize() first.",
                ErrorCode.BLOCKCHAIN_CONNECTION_ERROR
            )
        }
    }

    open fun isConnected(): Boolean {
        return isInitialized && gateway != null && network != null && contract != null
    }

    open fun getNetworkName(): String? = network?.channel?.name

    open fun getContractName(): String = config?.contractName ?: "unknown"

    open suspend fun reconnect(): Boolean = withContext(Dispatchers.IO) {
        try {
            logger.info { "Attempting to reconnect to Fabric network..." }
            close()
            initialize()
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to reconnect to Fabric network" }
            false
        }
    }

    open fun close() {
        try {
            gateway?.close()
            gateway = null
            network = null
            contract = null
            isInitialized = false
            logger.info { "Fabric Gateway Client closed" }
        } catch (e: Exception) {
            logger.warn(e) { "Error closing Fabric Gateway Client: ${e.message}" }
        }
    }
}