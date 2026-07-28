FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Payment Service depends on shared modules, so the Docker build context must
# be the repository root (see docker-compose.yml and the CI workflow).
COPY pom.xml ./
COPY common ./common
COPY payment-service ./payment-service

RUN mvn -pl payment-service -am package -DskipTests --no-transfer-progress

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/payment-service/target/*.jar app.jar

EXPOSE 8087 9097

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
