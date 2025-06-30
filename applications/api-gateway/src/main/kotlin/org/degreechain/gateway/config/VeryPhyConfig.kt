package org.degreechain.gateway.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import org.springframework.http.client.SimpleClientHttpRequestFactory
import java.time.Duration

/**
 * Configuration properties for VeryPhy API integration
 */
@Configuration
@ConfigurationProperties(prefix = "veryphy.api")
data class VeryPhyConfig(
    var baseUrl: String = "http://localhost:8000",
    var apiKey: String = "",
    var timeout: Duration = Duration.ofSeconds(30),
    var connectionTimeout: Duration = Duration.ofSeconds(10),
    var readTimeout: Duration = Duration.ofSeconds(30),
    var maxRetries: Int = 3,
    var retryDelay: Duration = Duration.ofSeconds(2),
    var maxFileSize: Long = 10485760, // 10MB
    var allowedFileTypes: List<String> = listOf("jpg", "jpeg", "png", "pdf"),
    var circuitBreakerEnabled: Boolean = true,
    var circuitBreakerFailureThreshold: Int = 5,
    var circuitBreakerTimeout: Duration = Duration.ofMinutes(1)
) {

    @Bean
    fun restTemplate(): RestTemplate {
        val factory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectionTimeout.toMillis().toInt())
            setReadTimeout(readTimeout.toMillis().toInt())
        }
        return RestTemplate(factory)
    }
}

/**
 * Integration configuration for the degree attestation system
 */
@Configuration
@ConfigurationProperties(prefix = "degree.attestation.integration")
data class IntegrationConfig(
    var enableVeryPhyIntegration: Boolean = true,
    var enableAsyncProcessing: Boolean = true,
    var maxConcurrentProcessing: Int = 10,
    var processingTimeout: Duration = Duration.ofMinutes(5),
    var tempFileDirectory: String = "/tmp/degree-attestation",
    var cleanupTempFiles: Boolean = true,
    var tempFileRetentionDuration: Duration = Duration.ofHours(1),
    var enableMetrics: Boolean = true,
    var enableDetailedLogging: Boolean = false,
    var enableCaching: Boolean = true,
    var cacheExpirationDuration: Duration = Duration.ofHours(24),
    var enableRateLimiting: Boolean = true,
    var rateLimitRequestsPerMinute: Int = 100
)

/**
 * File processing configuration
 */
@Configuration
@ConfigurationProperties(prefix = "file.processing")
data class FileProcessingConfig(
    var maxFileSize: Long = 10485760, // 10MB
    var allowedMimeTypes: List<String> = listOf(
        "image/jpeg", "image/png", "image/gif", "application/pdf"
    ),
    var allowedExtensions: List<String> = listOf("jpg", "jpeg", "png", "gif", "pdf"),
    var virusScanEnabled: Boolean = false,
    var imageOptimizationEnabled: Boolean = true,
    var maxImageWidth: Int = 2048,
    var maxImageHeight: Int = 2048,
    var jpegQuality: Float = 0.85f,
    var enableThumbnailGeneration: Boolean = true,
    var thumbnailSize: Int = 200
)

/**
 * Security configuration for API integration
 */
@Configuration
@ConfigurationProperties(prefix = "security.api")
data class ApiSecurityConfig(
    var enableApiKeyValidation: Boolean = true,
    var enableJwtValidation: Boolean = true,
    var enableRoleBasedAccess: Boolean = true,
    var enableIpWhitelisting: Boolean = false,
    var whitelistedIps: List<String> = emptyList(),
    var enableRequestSigning: Boolean = false,
    var signatureAlgorithm: String = "HmacSHA256",
    var enableAuditLogging: Boolean = true,
    var maxRequestsPerIp: Int = 1000,
    var rateLimitWindowDuration: Duration = Duration.ofHours(1)
)

/**
 * Monitoring and alerting configuration
 */
@Configuration
@ConfigurationProperties(prefix = "monitoring")
data class MonitoringConfig(
    var enableHealthChecks: Boolean = true,
    var healthCheckInterval: Duration = Duration.ofMinutes(1),
    var enableMetricsCollection: Boolean = true,
    var metricsRetentionDuration: Duration = Duration.ofDays(30),
    var enableAlerting: Boolean = true,
    var alertingThresholds: AlertingThresholds = AlertingThresholds(),
    var enablePerformanceTracking: Boolean = true,
    var slowRequestThreshold: Duration = Duration.ofSeconds(5)
)

/**
 * Alerting thresholds configuration
 */
data class AlertingThresholds(
    var errorRateThreshold: Double = 0.05, // 5%
    var responseTimeThreshold: Duration = Duration.ofSeconds(10),
    var diskSpaceThreshold: Double = 0.90, // 90%
    var memoryUsageThreshold: Double = 0.85, // 85%
    var cpuUsageThreshold: Double = 0.80 // 80%
)

/**
 * Database configuration for caching and temporary storage
 */
@Configuration
@ConfigurationProperties(prefix = "database.cache")
data class CacheConfig(
    var enableRedisCache: Boolean = false,
    var redisHost: String = "localhost",
    var redisPort: Int = 6379,
    var redisPassword: String? = null,
    var redisDatabase: Int = 0,
    var cacheDefaultTtl: Duration = Duration.ofHours(1),
    var cacheMaxSize: Long = 1000,
    var enableLocalCache: Boolean = true,
    var localCacheMaxSize: Long = 100
)

/**
 * Notification configuration
 */
@Configuration
@ConfigurationProperties(prefix = "notifications")
data class NotificationConfig(
    var enableEmailNotifications: Boolean = false,
    var enableSlackNotifications: Boolean = false,
    var enableWebhookNotifications: Boolean = false,
    var emailTemplatesPath: String = "classpath:email-templates/",
    var webhookUrl: String? = null,
    var slackWebhookUrl: String? = null,
    var notifyOnVerificationFailure: Boolean = true,
    var notifyOnSystemErrors: Boolean = true,
    var notifyOnHighLoad: Boolean = true
)