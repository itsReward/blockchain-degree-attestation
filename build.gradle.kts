plugins {
    kotlin("jvm") version "1.9.22" apply false
    kotlin("plugin.spring") version "1.9.22" apply false
    id("org.springframework.boot") version "3.2.2" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    id("org.jetbrains.kotlin.plugin.jpa") version "1.9.22" apply false
}

allprojects {
    group = "org.degreechain"
    version = "1.0.0"

    repositories {
        mavenCentral()
        maven { url = uri("https://hyperledger.jfrog.io/artifactory/besu-maven/") }
        maven { url = uri("https://packages.confluent.io/maven/") }
    }
}

subprojects {
    apply(plugin = "kotlin")

    dependencies {
        val implementation by configurations
        val testImplementation by configurations

        implementation("org.jetbrains.kotlin:kotlin-stdlib")
        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

        // Logging
        implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
        implementation("ch.qos.logback:logback-classic")

        // Testing
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.junit.jupiter:junit-jupiter")
        testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
        testImplementation("org.testcontainers:junit-jupiter")
        testImplementation("org.testcontainers:postgresql")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "17"
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

// Version management
ext {
    set("springBootVersion", "3.2.2")
    set("springCloudVersion", "2023.0.0")
    set("fabricSdkVersion", "2.2.25")
    set("kotlinCoroutinesVersion", "1.7.3")
    set("jacksonVersion", "2.16.1")
    set("testcontainersVersion", "1.19.3")
}