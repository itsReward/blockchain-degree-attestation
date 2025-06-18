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

# Copy the built jar
RUN cp applications/attestation-authority/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]