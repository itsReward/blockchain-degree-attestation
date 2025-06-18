package org.degreechain.authority.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

@Configuration
@EnableAsync
class ApplicationConfig {

    @Bean
    @Primary
    fun objectMapper(): ObjectMapper {
        return ObjectMapper()
            .registerKotlinModule()
            .registerModule(JavaTimeModule())
    }

    @Bean(name = ["taskExecutor"])
    fun taskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 20
        executor.queueCapacity = 25
        executor.setThreadNamePrefix("attestation-")
        executor.initialize()
        return executor
    }
}