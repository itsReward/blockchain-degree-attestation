package org.degreechain.authority.config

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.blockchain.FabricGatewayClient
import org.degreechain.blockchain.HealthChecker
import org.degreechain.blockchain.config.FabricConfig
import org.degreechain.blockchain.mock.MockContractInvoker
import org.degreechain.blockchain.mock.MockFabricGatewayClient
import org.degreechain.blockchain.mock.MockHealthChecker
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile

private val logger = KotlinLogging.logger {}

@Configuration
@EnableConfigurationProperties(FabricConfig::class)
class AttestationAuthorityBlockchainConfig {

    @Bean
    @Primary
    fun attestationFabricGatewayClient(fabricConfig: FabricConfig): FabricGatewayClient {
        return if (fabricConfig.networkConfigPath.isBlank() || fabricConfig.walletPath.isBlank()) {
            logger.warn { "Attestation Authority: Using mock blockchain implementation" }
            MockFabricGatewayClient()
        } else {
            logger.info { "Attestation Authority: Using real Fabric Gateway Client" }
            FabricGatewayClient(fabricConfig)
        }
    }

    @Bean
    @Primary
    fun attestationContractInvoker(fabricGatewayClient: FabricGatewayClient): ContractInvoker {
        return if (fabricGatewayClient is MockFabricGatewayClient) {
            logger.warn { "Attestation Authority: Using mock Contract Invoker" }
            MockContractInvoker()
        } else {
            logger.info { "Attestation Authority: Using real Contract Invoker" }
            ContractInvoker(fabricGatewayClient)
        }
    }

    @Bean
    @Primary
    fun attestationHealthChecker(contractInvoker: ContractInvoker): HealthChecker {
        return if (contractInvoker is MockContractInvoker) {
            logger.warn { "Attestation Authority: Using mock Health Checker" }
            MockHealthChecker()
        } else {
            logger.info { "Attestation Authority: Using real Health Checker" }
            HealthChecker(contractInvoker)
        }
    }

    @Bean
    @Profile("!test")
    fun initializeAttestationBlockchain(fabricGatewayClient: FabricGatewayClient): FabricGatewayClient {
        return if (fabricGatewayClient is MockFabricGatewayClient) {
            logger.info { "Attestation Authority: Skipping blockchain initialization - using mock" }
            fabricGatewayClient
        } else {
            runBlocking {
                try {
                    logger.info { "Attestation Authority: Initializing blockchain connection..." }
                    fabricGatewayClient.initialize()
                } catch (e: Exception) {
                    logger.error(e) { "Attestation Authority: Failed to initialize blockchain: ${e.message}" }
                    logger.warn { "Attestation Authority: Will continue with limited functionality" }
                    fabricGatewayClient
                }
            }
        }
    }
}
