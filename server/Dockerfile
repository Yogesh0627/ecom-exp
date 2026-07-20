# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the Spring Boot jar ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache dependencies first: only re-download when pom.xml changes, not on every source edit.
COPY pom.xml .
RUN mvn -q -B -o dependency:go-offline || mvn -q -B dependency:go-offline
COPY src ./src
# Tests need a live Postgres, so skip them in the image build; the CI/local run covers them.
RUN mvn -q -B clean package -DskipTests

# ---- Run stage: a slim JRE with just the jar ----
FROM eclipse-temurin:21-jre
WORKDIR /app
# Run as a non-root user.
RUN useradd -r -u 1001 ecoexpress
COPY --from=build /app/target/*.jar app.jar
USER ecoexpress
# The app binds to $PORT (Render injects it); 8081 is only the local default.
EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
