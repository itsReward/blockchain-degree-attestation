plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()

    // Official Hyperledger Fabric repository
    maven {
        url = uri("https://hyperledger.jfrog.io/hyperledger/fabric-maven")
        content {
            includeGroupByRegex("org\\.hyperledger\\.fabric.*")
        }
    }
}

dependencies {
    // Core Hyperledger Fabric Chaincode dependency - this is all you need for chaincode
    implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.3")

    // Fabric protos - use the separate fabric-protos artifact, not chaincode-protos
    implementation("org.hyperledger.fabric:fabric-protos:0.3.3")

    // Jackson for JSON processing - consistent versions
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Kotlin coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Validation - correct groupId
    implementation("commons-validator:commons-validator:1.7")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.3")
}

application {
    mainClass.set("org.hyperledger.fabric.contract.ContractRouter")
}

tasks.shadowJar {
    archiveBaseName.set("degree-attestation-chaincode")
    archiveClassifier.set("")
    archiveVersion.set("")

    // Exclude problematic files
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
    exclude("META-INF/*.RSA")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/NOTICE*")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

// Ensure Java 17 compatibility (required for Fabric 2.5+)
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = listOf("-Xjsr305=strict")
    }
}