package org.degreechain.gateway.models

data class IntegrationConfigRequest(
    val apiEndpoint: String,
    val apiKey: String,
    val apiSecret: String? = null,
    val timeout: Int = 30,
    val retryAttempts: Int = 3,
    val enableLogging: Boolean = true,
    val additionalHeaders: Map<String, String>? = null,
    val configuration: Map<String, Any>? = null
)