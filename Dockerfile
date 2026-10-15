# ==========================================
# Multi-stage Dockerfile for AIVES Backend
# Optimized for Cloud Deployment (Render, Railway, Fly.io, etc.)
# ==========================================

# Stage 1: Build JAR package
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# Cache maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create non-root user for security
RUN addgroup -S aives && adduser -S aives -G aives
USER aives

# Copy compiled jar
COPY --from=builder /build/target/*.jar app.jar

# Default Port (Render sets dynamic PORT env var)
ENV PORT=8080
EXPOSE 8080

# JVM Memory Tuning:
# Render Free Tier limits RAM to 512MB.
# MaxRAMPercentage=75.0 prevents Container OOMKilled by limiting heap to ~384MB.
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
