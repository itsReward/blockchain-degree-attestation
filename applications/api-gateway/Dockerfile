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
