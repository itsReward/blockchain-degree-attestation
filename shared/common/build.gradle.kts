// shared/common/build.gradle.kts
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
}

dependencies {
    // Jackson for JSON processing
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Validation
    api("org.springframework.boot:spring-boot-starter-validation:3.2.2")

    // Logging
    api("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

    // Crypto
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}