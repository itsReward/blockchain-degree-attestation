// shared/blockchain-client/src/main/kotlin/org/degreechain/blockchain/config/FabricConfig.kt
package org.degreechain.blockchain.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "fabric")
data class FabricConfig(
    val networkConfigPath: String,
    val walletPath: String,
    val userId: String,
    val organizationName: String,
    val channelName: String,
    val contractName: String,
    val discoveryEnabled: Boolean = true,
    val connectionTimeout: Long = 30000,
    val requestTimeout: Long = 30000,
    val tls: TlsConfig = TlsConfig()
)

data class TlsConfig(
    val enabled: Boolean = true,
    val certificatePath: String? = null,
    val keyPath: String? = null,
    val caCertificatePath: String? = null
)