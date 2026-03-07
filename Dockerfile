# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-24 AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src/ src/
RUN ./mvnw -q -DskipTests clean package

FROM eclipse-temurin:24-jre
ARG APP_NAME=splitfy-api
ARG APP_VERSION=local
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

LABEL org.opencontainers.image.title="${APP_NAME}" \
      org.opencontainers.image.version="${APP_VERSION}" \
      org.opencontainers.image.description="Splitfy REST API" \
      org.opencontainers.image.source="https://hub.docker.com"

# Run the app as a non-root user in the final image.
RUN useradd --create-home --shell /usr/sbin/nologin spring
USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
