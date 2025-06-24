plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    `java-library`
    `maven-publish`
}

group = "org.degreechain"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    // Shared common module
    api(project(":shared:common"))

    // LEGACY: Hyperledger Fabric Gateway Java SDK
    api("org.hyperledger.fabric:fabric-gateway-java:2.2.0")
    api("io.grpc:grpc-netty-shaded:1.58.0")

    // Spring Boot for configuration
    api("org.springframework.boot:spring-boot-starter:3.2.2")
    api("org.springframework.boot:spring-boot-configuration-processor:3.2.2")

    // Jackson for JSON processing
    api("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Coroutines
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.7.3")

    // Logging
    api("io.github.microutils:kotlin-logging-jvm:3.0.5")

    // Cryptography
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("org.testcontainers:junit-jupiter:1.19.3")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
