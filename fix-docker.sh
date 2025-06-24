#!/bin/bash

echo "🐳 Fixing Docker Build Issues"
echo "=============================="

# Fix the API Gateway Dockerfile
echo "🔧 Fixing API Gateway Dockerfile..."
cat > applications/api-gateway/Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy shared modules
COPY shared shared
COPY applications/api-gateway applications/api-gateway

# Build the application
RUN chmod +x gradlew
RUN ./gradlew :applications:api-gateway:build -x test

# Copy the built jar (find the main application jar, excluding plain jars)
RUN find applications/api-gateway/build/libs/ -name "*.jar" -not -name "*-plain.jar" -exec cp {} app.jar \;

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# Also fix other Dockerfiles to be consistent
echo "🔧 Fixing University Portal Dockerfile..."
cat > applications/university-portal/Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy shared modules
COPY shared shared
COPY applications/university-portal applications/university-portal

# Build the application
RUN chmod +x gradlew
RUN ./gradlew :applications:university-portal:build -x test

# Copy the built jar (find the main application jar, excluding plain jars)
RUN find applications/university-portal/build/libs/ -name "*.jar" -not -name "*-plain.jar" -exec cp {} app.jar \;

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

echo "🔧 Fixing Employer Portal Dockerfile..."
cat > applications/employer-portal/Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy shared modules
COPY shared shared
COPY applications/employer-portal applications/employer-portal

# Build the application
RUN chmod +x gradlew
RUN ./gradlew :applications:employer-portal:build -x test

# Copy the built jar (find the main application jar, excluding plain jars)
RUN find applications/employer-portal/build/libs/ -name "*.jar" -not -name "*-plain.jar" -exec cp {} app.jar \;

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

echo "🔧 Fixing Attestation Authority Dockerfile..."
cat > applications/attestation-authority/Dockerfile << 'EOF'
FROM openjdk:17-jdk-slim

WORKDIR /app

# Copy gradle wrapper and build files
COPY gradle gradle
COPY gradlew .
COPY build.gradle.kts .
COPY settings.gradle.kts .

# Copy shared modules
COPY shared shared
COPY applications/attestation-authority applications/attestation-authority

# Build the application
RUN chmod +x gradlew
RUN ./gradlew :applications:attestation-authority:build -x test

# Copy the built jar (find the main application jar, excluding plain jars)
RUN find applications/attestation-authority/build/libs/ -name "*.jar" -not -name "*-plain.jar" -exec cp {} app.jar \;

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

echo "✅ All Dockerfiles fixed!"
echo ""
echo "🧹 Cleaning up Docker images..."
docker-compose down 2>/dev/null || true
docker system prune -f 2>/dev/null || true

echo ""
echo "🚀 You can now run:"
echo "   ./quick-start.sh start"
echo ""
echo "📋 Changes made:"
echo "  ✅ Fixed all Dockerfile copy commands"
echo "  ✅ Using 'find' command instead of wildcards"
echo "  ✅ Consistent format across all services"
echo "  ✅ Cleaned up Docker cache"
