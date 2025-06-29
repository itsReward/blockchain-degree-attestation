package org.degreechain.employer.controllers

data class LogAuditEventRequest(
    val eventType: String,
    val description: String,
    val organizationName: String,
    val userId: String? = null,
    val ipAddress: String? = null,
    val userAgent: String? = null,
    val severity: String = "MEDIUM",
    val category: String,
    val metadata: Map<String, Any> = emptyMap(),
    val resourceId: String? = null,
    val outcome: String? = null
)

