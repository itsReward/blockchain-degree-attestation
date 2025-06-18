// fabric-network/chaincode/degree-attestation/build.gradle.kts
plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

dependencies {
    // Hyperledger Fabric
    implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.1")
    implementation("org.hyperledger.fabric:fabric-protos:0.3.0")

    // Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    implementation("ch.qos.logback:logback-classic:1.4.14")

    // Validation
    implementation("org.apache.commons:commons-validator:1.7")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.1")
    testImplementation("org.mockito:mockito-core:5.8.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
    testImplementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.1:test")
}

application {
    mainClass.set("org.hyperledger.fabric.contract.ContractRouter")
}

tasks.shadowJar {
    archiveBaseName.set("degree-attestation-chaincode")
    archiveClassifier.set("")
    archiveVersion.set("")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}