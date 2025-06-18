package org.degreechain.authority.models

import java.time.LocalDateTime

data class SystemMetrics(
    val timestamp: LocalDateTime,
    val blockchainHealth: BlockchainHealthMetrics,
    val applicationMetrics: ApplicationMetrics,
    val performanceMetrics: PerformanceMetrics
)

data class BlockchainHealthMetrics(
    val isConnected: Boolean,
    val responseTime: Long?,
    val lastBlockTime: LocalDateTime?,
    val pendingTransactions: Int,
    val networkNodes: Int,
    val consensusStatus: String
)

data class ApplicationMetrics(
    val activeUsers: Int,
    val requestsPerMinute: Double,
    val errorRate: Double,
    val memoryUsage: Double,
    val cpuUsage: Double,
    val diskUsage: Double
)

data class PerformanceMetrics(
    val averageResponseTime: Double,
    val p95ResponseTime: Double,
    val p99ResponseTime: Double,
    val throughput: Double,
    val errorCount: Int,
    val uptime: Double
)