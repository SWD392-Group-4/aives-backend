# syntax=docker/dockerfile:1

# Stage 1: Build JAR
FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Compile and package application
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Minimal Production JRE Image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy built JAR from builder
COPY --from=builder --chown=appuser:appgroup /app/target/*.jar app.jar

# Expose Spring Boot default port
EXPOSE 8080

# Configure JVM flags optimized for container environments (512MB - 1GB RAM)
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"
ENV SPRING_PROFILES_ACTIVE="supabase"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
