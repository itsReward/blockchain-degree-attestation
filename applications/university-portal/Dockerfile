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

# Copy the built jar
RUN cp applications/university-portal/build/libs/*.jar app.jar

# Expose port
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]