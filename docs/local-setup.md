# Local Setup Guide

This guide explains how to run the ecommerce microservices platform locally.

The current local platform includes:

- Config Server
- Auth Service
- Product Service
- Inventory Service
- Cart Service
- Order Service
- PostgreSQL
- Redis
- Kafka
- Zookeeper
- Zipkin
- Prometheus
- Grafana

---

## 1. Prerequisites

Install the following:

| Tool           | Version                           |
| -------------- | --------------------------------- |
| Java           | 21                                |
| Maven          | 3.9+                              |
| Docker Desktop | Latest                            |
| Git            | Latest                            |
| IDE            | IntelliJ IDEA / VS Code / Eclipse |

Verify:

```bash
java -version
mvn -version
docker --version
docker compose version
```

---

## 2. Clone Repository

```bash
git clone <your-repo-url>
cd ecommerce-platform
```

---

## 3. Environment Variables

Create a local `.env` file from the example file:

```bash
cp .env.example .env
```

For Windows PowerShell:

```powershell
Copy-Item .env.example .env
```

Default local values:

```env
CONFIG_SERVER_URL=http://localhost:8888
AUTH_ISSUER_URI=http://localhost:8081
AUTH_JWK_SET_URI=http://localhost:8081/oauth2/jwks
KAFKA_BOOTSTRAP_SERVERS=localhost:29092
```

Do not commit your real `.env`.

---

## 4. Start Local Infrastructure

From the project root:

```bash
docker compose up -d
```

This starts:

| Component               |  Port |
| ----------------------- | ----: |
| PostgreSQL              |  5433 |
| Redis                   |  6379 |
| Zookeeper               |  2181 |
| Kafka internal listener |  9092 |
| Kafka host listener     | 29092 |
| Zipkin                  |  9411 |
| Prometheus              |  9090 |
| Grafana                 |  3000 |

Check running containers:

```bash
docker ps
```

Expected containers:

```text
ecommerce-postgres
ecommerce-redis
ecommerce-zookeeper
ecommerce-kafka
zipkin
prometheus
grafana
```

---

## 5. PostgreSQL Database Setup

The services use separate logical databases:

| Service           | Database       |
| ----------------- | -------------- |
| Auth Service      | `auth_db`      |
| Product Service   | `product_db`   |
| Inventory Service | `inventory_db` |
| Order Service     | `order_db`     |

If these databases do not exist, create them manually:

```bash
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce
```

Then run:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
```

Exit:

```sql
\q
```

If the databases already exist, skip this step.

---

## 6. Kafka Setup

### Important Kafka Local Rule

Your Docker Compose has two Kafka listeners:

| Runtime                                      | Bootstrap Server  |
| -------------------------------------------- | ----------------- |
| Spring Boot services running locally on host | `localhost:29092` |
| Services running inside Docker network       | `kafka:9092`      |

For local Maven/IDE runs, use:

```yaml
spring:
    kafka:
        bootstrap-servers: localhost:29092
```

Do not use:

```yaml
localhost:9092
```

for host-running Spring Boot services.

---

## 7. Create Kafka Topics

Run:

```powershell
.\scripts\create-kafka-topics.ps1
```

Or create manually:

```powershell
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order-created --partitions 3 --replication-factor 1
```

Verify topics:

```powershell
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server kafka:9092 --list
```

Expected:

```text
order-created
```

Consume events:

```powershell
docker exec -it ecommerce-kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-created --from-beginning
```

---

## 8. Start Services

Start services in this order:

```text
1. Config Server
2. Auth Service
3. Product Service
4. Inventory Service
5. Cart Service
6. Order Service
```

---

## 9. Start Config Server

```bash
cd config-server
mvn spring-boot:run
```

Expected:

```text
Started ConfigServerApplication
Tomcat started on port 8888
```

Verify:

```text
http://localhost:8888/application/dev
```

Example service config check:

```powershell
Invoke-RestMethod http://localhost:8888/order-service/dev
```

---

## 10. Start Auth Service

```bash
cd auth-service
mvn spring-boot:run
```

Expected port:

```text
8081
```

Swagger:

```text
http://localhost:8081/swagger-ui.html
```

JWK endpoint:

```text
http://localhost:8081/oauth2/jwks
```

Health:

```text
http://localhost:8081/actuator/health
```

---

## 11. Start Product Service

```bash
cd product-service
mvn spring-boot:run
```

Expected port:

```text
8082
```

Swagger:

```text
http://localhost:8082/swagger-ui.html
```

Health:

```text
http://localhost:8082/actuator/health
```

---

## 12. Start Inventory Service

```bash
cd inventory-service
mvn spring-boot:run
```

Expected REST port:

```text
8084
```

Expected gRPC port:

```text
9091
```

Swagger:

```text
http://localhost:8084/swagger-ui.html
```

Health:

```text
http://localhost:8084/actuator/health
```

Inventory Service exposes gRPC APIs used by Order Service:

```text
ReserveStock()
ReleaseStock()
DeductStock()
GetInventory()
```

---

## 13. Start Cart Service

```bash
cd cart-service
mvn spring-boot:run
```

Expected port:

```text
8085
```

Swagger:

```text
http://localhost:8085/swagger-ui.html
```

Health:

```text
http://localhost:8085/actuator/health
```

Cart storage pattern in Redis:

```text
cart:{userId}
```

---

## 14. Start Order Service

```bash
cd order-service
mvn spring-boot:run
```

Expected port:

```text
8086
```

Swagger:

```text
http://localhost:8086/swagger-ui.html
```

Health:

```text
http://localhost:8086/actuator/health
```

Prometheus metrics:

```text
http://localhost:8086/actuator/prometheus
```

Order Service depends on:

| Dependency        | Purpose                       |
| ----------------- | ----------------------------- |
| Auth Service      | OAuth2 JWT validation         |
| Inventory Service | gRPC stock reservation        |
| Kafka             | Publish `order-created` event |
| PostgreSQL        | Persist orders                |
| Config Server     | Load runtime configuration    |

---

## 15. Swagger URLs

| Service           | Swagger URL                             |
| ----------------- | --------------------------------------- |
| Auth Service      | `http://localhost:8081/swagger-ui.html` |
| Product Service   | `http://localhost:8082/swagger-ui.html` |
| Inventory Service | `http://localhost:8084/swagger-ui.html` |
| Cart Service      | `http://localhost:8085/swagger-ui.html` |
| Order Service     | `http://localhost:8086/swagger-ui.html` |

---

## 16. Service Ports

| Service           | Port |
| ----------------- | ---: |
| Config Server     | 8888 |
| Auth Service      | 8081 |
| Product Service   | 8082 |
| Inventory Service | 8084 |
| Cart Service      | 8085 |
| Order Service     | 8086 |
| Inventory gRPC    | 9091 |

Infrastructure ports:

| Component             |  Port |
| --------------------- | ----: |
| PostgreSQL            |  5433 |
| Redis                 |  6379 |
| Kafka host listener   | 29092 |
| Kafka Docker listener |  9092 |
| Zookeeper             |  2181 |
| Zipkin                |  9411 |
| Prometheus            |  9090 |
| Grafana               |  3000 |

---

## 17. OAuth2 Local Flow

### Register User

Use:

```text
api/auth.http
```

or Swagger:

```text
POST http://localhost:8081/api/v1/auth/register
```

Example:

```json
{
    "name": "Test Customer",
    "email": "customer@example.com",
    "password": "Password@123"
}
```

### Login

```text
POST http://localhost:8081/api/v1/auth/login
```

Example:

```json
{
    "email": "customer@example.com",
    "password": "Password@123"
}
```

Copy from response:

```text
accessToken
refreshToken
user.id
```

Use access token in Swagger Authorize button:

```text
Bearer <access-token>
```

---

## 18. Order Flow Smoke Test

Before creating an order:

1. Auth Service must be running.
2. Inventory Service must be running.
3. Kafka must be running.
4. `order-created` topic must exist.
5. Product/inventory data must exist for the product ID.

### Create Order

Use:

```text
api/order.http
```

or call:

```http
POST http://localhost:8086/api/v1/orders
Authorization: Bearer <access-token>
Content-Type: application/json
```

Body:

```json
{
    "items": [
        {
            "productId": "11111111-1111-1111-1111-111111111111",
            "quantity": 2,
            "price": 100.0
        }
    ]
}
```

Expected:

| System       | Expected Result                 |
| ------------ | ------------------------------- |
| Order DB     | Order saved as `PENDING`        |
| Inventory    | Stock reserved                  |
| Kafka        | `order-created` event published |
| API Response | `OrderResponse` returned        |

Verify Kafka event:

```powershell
docker exec -it ecommerce-kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-created --from-beginning
```

---

## 19. Run Tests

Run tests for one service:

```bash
cd auth-service
mvn clean test
```

Run core services from root:

```bash
mvn -pl auth-service,product-service,inventory-service,cart-service,order-service -am clean test
```

Run Order Service with dependencies:

```bash
mvn -pl order-service -am clean test
```

---

## 20. Stop Infrastructure

Stop containers:

```bash
docker compose down
```

Stop and remove volumes only if you want to reset all local data:

```bash
docker compose down -v
```

Be careful: `down -v` deletes PostgreSQL, Redis, Kafka, and Grafana volumes.

---

## 21. Common Local Issues

### Missing JwtDecoder

Error:

```text
No qualifying bean of type JwtDecoder available
```

Fix:

Make sure service config contains:

```yaml
spring:
    security:
        oauth2:
            resourceserver:
                jwt:
                    issuer-uri: http://localhost:8081
                    jwk-set-uri: http://localhost:8081/oauth2/jwks
```

Also make sure Auth Service is running.

---

### Swagger Returns 401

Make sure the service `SecurityConfig` whitelists:

```text
/swagger-ui.html
/swagger-ui/**
/v3/api-docs
/v3/api-docs/**
/v3/api-docs.yaml
```

---

### Kafka Keeps Connecting to localhost:9092

For Spring Boot running locally, use:

```yaml
spring:
    kafka:
        bootstrap-servers: localhost:29092
```

If logs show:

```text
bootstrap.servers = [localhost:9092]
```

then the running service is still reading the wrong config.

Check Config Server:

```powershell
Invoke-RestMethod http://localhost:8888/order-service/dev | ConvertTo-Json -Depth 30 | Select-String "bootstrap"
```

Expected:

```text
localhost:29092
```

---

### Kafka Cluster ID Mismatch

This usually means Kafka data belongs to an old Zookeeper cluster.

For local development:

```powershell
docker compose stop kafka zookeeper
docker compose rm -f kafka zookeeper
docker volume rm ecommerce-platform_kafka-data
docker compose up -d
```

Do not remove all volumes unless you want to reset PostgreSQL and Redis too.

---

### Kafka Cannot Serialize OrderCreatedEvent

Error:

```text
Can't convert value of class OrderCreatedEvent to StringSerializer
```

Fix producer config:

```yaml
spring:
    kafka:
        producer:
            key-serializer: org.apache.kafka.common.serialization.StringSerializer
            value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
            properties:
                spring.json.add.type.headers: false
```

---

### Prometheus Endpoint Fails with ApiErrorResponse

If `/actuator/prometheus` fails with an OpenMetrics conversion error, scope the global exception handler:

```java
@RestControllerAdvice(basePackages = "com.ecommerce")
public class GlobalExceptionHandler {
}
```

---

## 22. Local Development Checklist

Before testing business APIs, confirm:

- [ ] Docker containers are running
- [ ] PostgreSQL databases exist
- [ ] Redis is running
- [ ] Kafka is running
- [ ] `order-created` topic exists
- [ ] Config Server is running
- [ ] Auth Service is running
- [ ] Inventory Service is running
- [ ] Order Service config uses `localhost:29092`
- [ ] Auth token is available
- [ ] Swagger pages load

---

## 23. Recommended Startup Order

```text
1. docker compose up -d
2. create Kafka topics
3. start Config Server
4. start Auth Service
5. start Product Service
6. start Inventory Service
7. start Cart Service
8. start Order Service
9. login through Auth Service
10. test Order flow
```
