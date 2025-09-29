# Multi-stage Dockerfile for KochiRailMetro (Java 17, Spring Boot)
# Build stage
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy only pom.xml first for better caching
COPY pom.xml ./

# Pre-fetch dependencies (optional but speeds up builds)
RUN mvn -q -B -DskipTests dependency:go-offline || true

# Copy sources and build
COPY src/ src/
RUN mvn -q -B -DskipTests clean package

# Runtime stage
FROM eclipse-temurin:17-jre

ENV APP_HOME=/app \
    SERVER_PORT=8091 \
    SPRING_PROFILES_ACTIVE=docker

WORKDIR ${APP_HOME}

# Create folders for persistent data/logs
RUN mkdir -p ${APP_HOME}/uploads ${APP_HOME}/logs ${APP_HOME}/credentials

# Copy fat jar from build stage
COPY --from=builder /workspace/target/*-SNAPSHOT.jar ${APP_HOME}/app.jar

EXPOSE 8091

# JVM & Spring config can be overridden with env vars
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

# If you add an actuator health endpoint, you can enable a HEALTHCHECK here
# HEALTHCHECK --interval=30s --timeout=5s --retries=5 CMD wget -qO- http://localhost:${SERVER_PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -Dserver.port=${SERVER_PORT} -Dspring.profiles.active=${SPRING_PROFILES_ACTIVE} -jar ${APP_HOME}/app.jar"]
