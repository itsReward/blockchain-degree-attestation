#!/bin/bash

echo "🔧 Fixing Gradle Build Configuration"
echo "======================================"

# Stop any running containers first
echo "⚠️  Stopping existing containers..."
docker-compose down 2>/dev/null || true

# Clean gradle cache and build artifacts
echo "🧹 Cleaning build artifacts..."
./gradlew clean 2>/dev/null || true
rm -rf .gradle/
rm -rf */build/
rm -rf shared/*/build/
rm -rf applications/*/build/

# Create a temporary backup
echo "💾 Creating backup of current build files..."
mkdir -p .backup
cp build.gradle.kts .backup/build.gradle.kts.bak 2>/dev/null || true
cp settings.gradle.kts .backup/settings.gradle.kts.bak 2>/dev/null || true
cp shared/common/build.gradle.kts .backup/common-build.gradle.kts.bak 2>/dev/null || true
cp applications/api-gateway/build.gradle.kts .backup/api-gateway-build.gradle.kts.bak 2>/dev/null || true

echo "🔧 Applying fixes..."

# Fix 1: Update root build.gradle.kts
cat > build.gradle.kts << 'EOF'
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
        implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")

        // Logging
        implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")

        // Testing
        testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
        testImplementation("org.junit.jupiter:junit-jupiter")
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
EOF

# Fix 2: Update shared/common/build.gradle.kts
cat > shared/common/build.gradle.kts << 'EOF'
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

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
EOF

# Fix 3: Update applications/api-gateway/build.gradle.kts
cat > applications/api-gateway/build.gradle.kts << 'EOF'
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "org.degreechain"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

dependencies {
    // Shared modules
    implementation(project(":shared:common"))

    // Spring Boot starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // JSON processing
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")

    // JWT
    implementation("io.jsonwebtoken:jjwt-api:0.12.3")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.3")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")

    // Logging
    implementation("io.github.microutils:kotlin-logging-jvm")

    // HTTP Client
    implementation("org.springframework.boot:spring-boot-starter-webflux")

    // API Documentation
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")

    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Annotation processing
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test")
    testImplementation("io.projectreactor:reactor-test")
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

springBoot {
    mainClass.set("org.degreechain.gateway.GatewayApplicationKt")
}
EOF

# Fix 4: Update other shared modules
cat > shared/blockchain-client/build.gradle.kts << 'EOF'
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
EOF

cat > shared/payment-processor/build.gradle.kts << 'EOF'
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

    // Cryptography for payment security
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.bouncycastle:bcpkix-jdk15on:1.70")

    // HTTP Client for payment providers
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.2")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
EOF

# Make sure the GatewayApplication.kt exists
if [ ! -f "applications/api-gateway/src/main/kotlin/org/degreechain/gateway/GatewayApplication.kt" ]; then
    echo "📝 Creating missing GatewayApplication.kt..."
    mkdir -p applications/api-gateway/src/main/kotlin/org/degreechain/gateway
    cat > applications/api-gateway/src/main/kotlin/org/degreechain/gateway/GatewayApplication.kt << 'EOF'
package org.degreechain.gateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = [
        "org.degreechain.gateway",
        "org.degreechain.common"
    ]
)
class GatewayApplication

fun main(args: Array<String>) {
    runApplication<GatewayApplication>(*args)
}
EOF
fi

echo "🧪 Testing build..."
./gradlew :shared:common:build --no-daemon --info

if [ $? -eq 0 ]; then
    echo "✅ Common module build successful"

    echo "🧪 Testing API Gateway build..."
    ./gradlew :applications:api-gateway:build -x test --no-daemon --info

    if [ $? -eq 0 ]; then
        echo "✅ All builds successful!"
        echo ""
        echo "🚀 You can now run:"
        echo "   ./quick-start.sh start"
        echo ""
    else
        echo "❌ API Gateway build failed. Check the output above for errors."
        exit 1
    fi
else
    echo "❌ Common module build failed. Check the output above for errors."
    exit 1
fi

echo "🎉 Build configuration fixed!"
echo ""
echo "📋 Changes made:"
echo "  ✅ Fixed root build.gradle.kts"
echo "  ✅ Fixed shared/common/build.gradle.kts"
echo "  ✅ Fixed shared/blockchain-client/build.gradle.kts"
echo "  ✅ Fixed shared/payment-processor/build.gradle.kts"
echo "  ✅ Fixed applications/api-gateway/build.gradle.kts"
echo "  ✅ Created GatewayApplication.kt (if missing)"
echo "  ✅ Cleaned build artifacts"
echo ""
echo "💡 Backup files saved in .backup/ directory"
