package org.degreechain.gateway.services

data class RateLimitResult(
    val allowed: Boolean,
    val currentCount: Int,
    val limit: Int,
    val resetTime: String,
    val retryAfter: Long?
)