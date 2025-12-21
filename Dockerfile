# Build stage
FROM gradle:8.5-jdk17 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle

# Copy source code
COPY src ./src

# Build with shadowjar
RUN ./gradlew shadowJar --no-daemon

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Copy the built jar from build stage
COPY --from=build /app/build/libs/bmc-manager.jar ./bmc-manager.jar

# Run the jar
ENTRYPOINT ["java", "-jar", "bmc-manager.jar"]