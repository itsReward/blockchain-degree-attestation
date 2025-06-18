package org.degreechain.authority.config

import kotlinx.coroutines.runBlocking
import org.degreechain.blockchain.ContractInvoker
import org.degreechain.blockchain.FabricGatewayClient
import org.degreechain.blockchain.HealthChecker
import org.degreechain.blockchain.config.FabricConfig
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
@EnableConfigurationProperties(FabricConfig::class)
class BlockchainConfig {

    @Bean
    fun fabricGatewayClient(fabricConfig: FabricConfig): FabricGatewayClient {
        return FabricGatewayClient(fabricConfig)
    }

    @Bean
    fun contractInvoker(fabricGatewayClient: FabricGatewayClient): ContractInvoker {
        return ContractInvoker(fabricGatewayClient)
    }

    @Bean
    fun healthChecker(contractInvoker: ContractInvoker): HealthChecker {
        return HealthChecker(contractInvoker)
    }

    @Bean
    @Profile("!test")
    fun initializeBlockchainConnection(fabricGatewayClient: FabricGatewayClient): FabricGatewayClient {
        return runBlocking {
            try {
                fabricGatewayClient.initialize()
            } catch (e: Exception) {
                // Log the error but don't fail startup - blockchain might not be ready yet
                println("Warning: Failed to initialize blockchain connection: ${e.message}")
                fabricGatewayClient
            }
        }
    }
}