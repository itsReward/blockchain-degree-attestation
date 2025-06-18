package org.degreechain.authority.config

import org.degreechain.blockchain.ContractInvoker
import org.degreechain.blockchain.FabricGatewayClient
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
    @Profile("!test")
    fun initializeBlockchainConnection(fabricGatewayClient: FabricGatewayClient): FabricGatewayClient {
        return runBlocking {
            fabricGatewayClient.initialize()
        }
    }
}