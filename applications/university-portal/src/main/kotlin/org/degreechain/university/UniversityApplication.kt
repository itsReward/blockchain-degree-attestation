package org.degreechain.university

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["org.degreechain"])
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
class UniversityApplication

fun main(args: Array<String>) {
    runApplication<UniversityApplication>(*args)
}