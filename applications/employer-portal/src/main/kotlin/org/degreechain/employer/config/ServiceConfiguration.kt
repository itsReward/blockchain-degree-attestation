package org.degreechain.employer.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.multipart.MultipartResolver
import org.springframework.web.multipart.support.StandardServletMultipartResolver
import java.util.concurrent.Executor
import java.util.concurrent.Executors

@Configuration
class ServiceConfiguration {

    @Bean
    fun multipartResolver(): MultipartResolver {
        return StandardServletMultipartResolver()
    }

    @Bean(name = ["verificationExecutor"])
    fun verificationExecutor(): Executor {
        return Executors.newFixedThreadPool(10)
    }

    @Bean(name = ["paymentExecutor"])
    fun paymentExecutor(): Executor {
        return Executors.newFixedThreadPool(5)
    }
}