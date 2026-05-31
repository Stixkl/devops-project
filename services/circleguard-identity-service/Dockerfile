# syntax=docker/dockerfile:1

# ---- Build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy Gradle wrapper and build configuration first (better layer caching)
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle ./gradle

# Copy all service sources (multimodule build needs full project)
COPY services ./services

RUN chmod +x ./gradlew &&     ./gradlew :services:circleguard-identity-service:bootJar --no-daemon

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Run as non-root user
RUN groupadd --system app && useradd --system --gid app --no-create-home app

COPY --from=build /workspace/services/circleguard-identity-service/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8083
ENV JAVA_OPTS=""
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
