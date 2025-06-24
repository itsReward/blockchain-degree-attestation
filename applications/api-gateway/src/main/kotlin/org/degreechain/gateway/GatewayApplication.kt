package org.degreechain.gateway

import mu.KotlinLogging
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.client.RestTemplate
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.context.event.EventListener
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.boot.info.BuildProperties
import org.springframework.core.env.Environment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

private val logger = KotlinLogging.logger {}

@SpringBootApplication(
    scanBasePackages = [
        "org.degreechain.gateway",
        "org.degreechain.common"
    ]
)
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class GatewayApplication {

    @Value("\${spring.application.name:api-gateway}")
    private lateinit var applicationName: String

    @Value("\${server.port:8080}")
    private var serverPort: Int = 8080

    @Value("\${management.endpoints.web.exposure.include:health,info,metrics}")
    private lateinit var actuatorEndpoints: String

    @Autowired
    private lateinit var environment: Environment

    /**
     * Password encoder bean for authentication
     */
    @Bean
    open fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder(12) // Strong encryption strength
    }

    /**
     * RestTemplate bean for synchronous HTTP calls to microservices
     */
    @Bean
    fun restTemplate(): RestTemplate {
        val restTemplate = RestTemplate()

        // Add interceptors for logging and monitoring
        restTemplate.interceptors.add { request, body, execution ->
            logger.debug { "Outgoing request: ${request.method} ${request.uri}" }
            val response = execution.execute(request, body)
            logger.debug { "Response status: ${response.statusCode}" }
            response
        }

        return restTemplate
    }

    /**
     * WebClient bean for reactive HTTP calls
     */
    @Bean
    fun webClient(): WebClient {
        return WebClient.builder()
            .codecs { configurer ->
                configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) // 10MB
            }
            .filter { request, next ->
                logger.debug { "WebClient request: ${request.method()} ${request.url()}" }
                next.exchange(request)
            }
            .build()
    }

    /**
     * Application startup event listener
     */
    @EventListener(ApplicationReadyEvent::class)
    fun onApplicationReady() {
        printStartupInfo()
        validateConfiguration()
        logServiceUrls()
    }

    /**
     * Print startup information
     */
    private fun printStartupInfo() {
        val activeProfiles = environment.activeProfiles.takeIf { it.isNotEmpty() }
            ?: arrayOf("default")

        logger.info {
            """
            
            =====================================
            🚀 API Gateway Started Successfully
            =====================================
            Application: $applicationName
            Port: $serverPort
            Profiles: ${activeProfiles.joinToString(", ")}
            Actuator: http://localhost:$serverPort/actuator
            Health Check: http://localhost:$serverPort/actuator/health
            Swagger UI: http://localhost:$serverPort/swagger-ui.html
            API Docs: http://localhost:$serverPort/api-docs
            =====================================
            
            """.trimIndent()
        }
    }

    /**
     * Validate critical configuration
     */
    private fun validateConfiguration() {
        val criticalProperties = mapOf(
            "jwt.secret" to environment.getProperty("jwt.secret"),
            "services.attestation-authority.url" to environment.getProperty("services.attestation-authority.url"),
            "services.university-portal.url" to environment.getProperty("services.university-portal.url"),
            "services.employer-portal.url" to environment.getProperty("services.employer-portal.url")
        )

        val missingProperties = criticalProperties.filterValues { it.isNullOrBlank() }

        if (missingProperties.isNotEmpty()) {
            logger.warn { "Missing configuration properties: ${missingProperties.keys}" }
        }

        // Validate JWT secret strength
        val jwtSecret = environment.getProperty("jwt.secret")
        if (jwtSecret != null && jwtSecret.length < 32) {
            logger.warn { "JWT secret is too short for production use (minimum 32 characters recommended)" }
        }

        logger.info { "Configuration validation completed" }
    }

    /**
     * Log configured service URLs
     */
    private fun logServiceUrls() {
        val services = mapOf(
            "Attestation Authority" to environment.getProperty("services.attestation-authority.url"),
            "University Portal" to environment.getProperty("services.university-portal.url"),
            "Employer Portal" to environment.getProperty("services.employer-portal.url"),
            "Redis" to "${environment.getProperty("spring.redis.host")}:${environment.getProperty("spring.redis.port")}"
        )

        logger.info { "Configured service endpoints:" }
        services.forEach { (name, url) ->
            logger.info { "  $name: $url" }
        }
    }
}

/**
 * Main application entry point
 */
fun main(args: Array<String>) {
    // Set system properties for better JVM performance
    System.setProperty("java.awt.headless", "true")
    System.setProperty("file.encoding", "UTF-8")

    // Configure JVM for containerized environments
    if (System.getenv("KUBERNETES_SERVICE_HOST") != null) {
        System.setProperty("java.util.concurrent.ForkJoinPool.common.parallelism", "2")
        logger.info { "Detected Kubernetes environment - optimizing JVM settings" }
    }

    // Start the application
    logger.info { "Starting DegreeChain API Gateway..." }

    try {
        val context = runApplication<GatewayApplication>(*args)

        // Log successful startup
        val environment = context.environment
        val port = environment.getProperty("server.port", "8080")
        val contextPath = environment.getProperty("server.servlet.context-path", "")

        logger.info {
            "API Gateway is running at: http://localhost:$port$contextPath"
        }

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutting down API Gateway gracefully..." }
        })

    } catch (e: Exception) {
        logger.error(e) { "Failed to start API Gateway" }
        System.exit(1)
    }
}