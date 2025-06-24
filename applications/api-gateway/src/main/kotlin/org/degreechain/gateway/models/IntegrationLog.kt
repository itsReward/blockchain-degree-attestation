package org.degreechain.gateway.models

import java.time.LocalDateTime

data class IntegrationLog(
    val timestamp: LocalDateTime,
    val provider: String,
    val operationType: String,
    val status: String,
    val message: String,
    val responseTime: Long? = null
)
