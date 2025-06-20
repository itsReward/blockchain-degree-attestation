package org.degreechain.employer

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "org.degreechain.employer",
        "org.degreechain.common",
        "org.degreechain.blockchain"
    ]
)
class EmployerPortalApplication

fun main(args: Array<String>) {
    runApplication<EmployerPortalApplication>(*args)
}