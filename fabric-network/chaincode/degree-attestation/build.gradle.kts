plugins {
    kotlin("jvm")
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

repositories {
    mavenCentral()
    // Add Maven repository explicitly
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    // Add Hyperledger Fabric repository
    maven {
        url = uri("https://hyperledger.jfrog.io/artifactory/fabric-maven")
    }
    // Only if needed and you have credentials
    maven {
        url = uri("https://jitpack.io")
        credentials {
            // Use project properties or system environment variables
            username = project.findProperty("jitpackUsername") as String? ?: System.getenv("JITPACK_USERNAME") ?: ""
            password = project.findProperty("jitpackToken") as String? ?: System.getenv("JITPACK_TOKEN") ?: ""
        }
    }
}



dependencies {
    // Hyperledger Fabric
    implementation("org.hyperledger.fabric-chaincode-java:fabric-chaincode-shim:2.5.1")
    implementation("org.hyperledger.fabric:fabric-protos:0.3.0")
    /*implementation("org.hyperledger.fabric:fabric-contract-api:2.5.1")
    implementation("org.hyperledger.fabric:fabric-shim:2.5.1")*/

    implementation("org.hyperledger.fabric:fabric-contract-api:2.2.19")
    implementation("org.hyperledger.fabric:fabric-shim:2.2.19")
    implementation("org.hyperledger.fabric:fabric-gateway:2.2.19")


// Jackson for JSON processing
    implementation("com.fasterxml.jackson.core:jackson-core:2.16.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.16.1")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.16.1")

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