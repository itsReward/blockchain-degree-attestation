package org.degreechain.blockchain.config

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.blockchain.FabricGatewayClient
import org.degreechain.blockchain.HealthChecker
import org.degreechain.blockchain.mock.MockContractInvoker
import org.degreechain.blockchain.mock.MockFabricGatewayClient
import org.degreechain.blockchain.mock.MockHealthChecker
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@EnableConfigurationProperties(FabricConfig::class)
class SharedBlockchainConfig {

    @Bean
    @ConditionalOnMissingBean
    fun fabricGatewayClient(fabricConfig: FabricConfig): FabricGatewayClient {
        return if (fabricConfig.networkConfigPath.isBlank() || fabricConfig.walletPath.isBlank()) {
            logger.warn { "Blockchain configuration incomplete, using mock implementation" }
            MockFabricGatewayClient()
        } else {
            logger.info { "Using real Fabric Gateway Client with config: ${fabricConfig.networkConfigPath}" }
            FabricGatewayClient(fabricConfig)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun contractInvoker(fabricGatewayClient: FabricGatewayClient): ContractInvoker {
        return if (fabricGatewayClient is MockFabricGatewayClient) {
            logger.warn { "Using mock Contract Invoker" }
            MockContractInvoker()
        } else {
            logger.info { "Using real Contract Invoker" }
            ContractInvoker(fabricGatewayClient)
        }
    }

    @Bean
    @ConditionalOnMissingBean
    fun healthChecker(contractInvoker: ContractInvoker): HealthChecker {
        return if (contractInvoker is MockContractInvoker) {
            logger.warn { "Using mock Health Checker" }
            MockHealthChecker()
        } else {
            logger.info { "Using real Health Checker" }
            HealthChecker(contractInvoker)
        }
    }

    @Bean
    @Profile("!test")
    @ConditionalOnMissingBean(name = ["initializeBlockchainConnection"])
    fun initializeBlockchainConnection(fabricGatewayClient: FabricGatewayClient): FabricGatewayClient {
        return if (fabricGatewayClient is MockFabricGatewayClient) {
            logger.info { "Skipping blockchain initialization - using mock implementation" }
            fabricGatewayClient
        } else {
            runBlocking {
                try {
                    logger.info { "Initializing real blockchain connection..." }
                    fabricGatewayClient.initialize()
                } catch (e: Exception) {
                    logger.error(e) { "Failed to initialize blockchain connection: ${e.message}" }
                    logger.warn { "Application will continue with limited functionality" }
                    fabricGatewayClient
                }
            }
        }
    }
}
