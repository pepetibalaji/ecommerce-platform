# Local Setup Guide

This guide explains how to run the ecommerce microservices platform locally.

The current local platform includes:

- Config Server
- API Gateway
- Auth Service
- Product Service
- Inventory Service
- Cart Service
- Order Service
- Payment Service
- PostgreSQL
- Redis
- Kafka
- OpenTelemetry Collector
- Tempo
- Loki
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
| Kafka internal listener |  9092 |
| Kafka host listener     | 29092 |
| Tempo HTTP API          |  3200 |
| Loki                    |  3100 |
| OpenTelemetry gRPC      |  4317 |
| OpenTelemetry HTTP      |  4318 |
| Prometheus              |  9090 |
| Grafana                 |  3000 |

Check running containers:

```bash
docker ps
```

Expected containers:

```text
ecommerce-postgres
ecommerce-mongodb
ecommerce-redis
ecommerce-kafka
ecommerce-kafka-init
ecommerce-otel-collector
ecommerce-prometheus
ecommerce-grafana
```

---

## 5. Database Setup

Auth, Inventory, Order, and Payment own separate logical PostgreSQL databases;
Product Service owns a MongoDB database:

| Service           | Database       |
| ----------------- | -------------- |
| Auth Service      | `auth_db`      |
| Product Service   | MongoDB `product_db` |
| Inventory Service | `inventory_db` |
| Order Service     | `order_db`     |
| Payment Service   | `payment_db`   |

`docker compose up -d` starts PostgreSQL, MongoDB, and Redis without a Compose profile.
To use those local stores from Maven/IDE services, point runtime configuration
at `localhost:5433`, `localhost:27017`, and `localhost:6379`. For services
running inside the Compose network, use `postgres:5432`, `mongodb:27017`, and
`redis:6379`. Product Service uses
`PRODUCT_MONGODB_URI=mongodb://localhost:27017/product_db` and
`PRODUCT_MONGODB_DATABASE=product_db`.

Create the logical service databases if required:

```bash
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce -c "CREATE DATABASE auth_db;"
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce -c "CREATE DATABASE inventory_db;"
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce -c "CREATE DATABASE order_db;"
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce -c "CREATE DATABASE payment_db;"
```

Update the local PostgreSQL datasource and Product Service MongoDB values in
the runtime environment file before starting services; never add them to
service `application.yml` files.

The equivalent SQL is:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
CREATE DATABASE payment_db;
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

Kafka topics are created automatically by the `kafka-init` service when you run
`docker compose up -d`.

To recreate an individual topic manually:

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
payment-success
payment-failed
order-dlq
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
7. Payment Service
8. API Gateway
```

---

## 9. Start Config Server

> **Current configuration-layout caveat:** Config Server has no Git
> `search-paths` setting, while the referenced configuration repository stores
> files in `dev/`, `stage/`, and `prod/` directories. Resolve that mismatch
> before relying on `/service/profile` responses for a full local bootstrap.

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
| Payment Service   | `http://localhost:8087/swagger-ui.html` |
| API Gateway       | `http://localhost:8080/swagger-ui.html` when dev/stage aggregation routes are active |

---

## 16. Service Ports

| Service           | Port |
| ----------------- | ---: |
| Config Server     | 8888 |
| API Gateway       | 8080 |
| Auth Service      | 8081 |
| Product Service   | 8082 |
| Inventory Service | 8084 |
| Cart Service      | 8085 |
| Order Service     | 8086 |
| Payment Service   | 8087 |
| Inventory gRPC    | 9091 |
| Payment gRPC      | 9092 |

Infrastructure ports:

| Component             |  Port |
| --------------------- | ----: |
| PostgreSQL            |  5433 |
| Redis                 |  6379 |
| Kafka host listener   | 29092 |
| Kafka Docker listener |  9092 |
| Tempo HTTP API        |  3200 |
| Loki                  |  3100 |
| OpenTelemetry gRPC    |  4317 |
| OpenTelemetry HTTP    |  4318 |
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

### Payment Outcome Smoke Test

After the provider has sent a verified payment webhook, Payment Service publishes
an outcome event and Order Service consumes it.

```text
payment-success  -> PENDING order becomes CONFIRMED; its inventory reservation stays held
payment-failed   -> PENDING order becomes PAYMENT_FAILED; a durable release command returns stock
```

1. Create an order and save its ID as `<order-id>`.
2. Complete or fail its checkout flow using the configured payment provider. The
   provider webhook, rather than the browser success URL, is the source of truth.
3. Verify the payment outcome event was published:

```powershell
docker exec -it ecommerce-kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic payment-success --from-beginning
```

Use `--topic payment-failed` when testing a failed payment.

4. Retrieve the order after the listener has processed the event:

```http
GET http://localhost:8086/api/v1/orders/<order-id>
Authorization: Bearer <access-token>
```

Expected successful-payment response fields:

```json
{
  "status": "CONFIRMED",
  "paymentId": "<payment-id>",
  "paymentConfirmedAt": "<timestamp>"
}
```

For a failed payment, expect `status: PAYMENT_FAILED`, `paymentId`,
`paymentFailedAt`, and optionally `paymentFailureReason`. Within the default
five-second release-worker interval, Inventory Service should move that item's
reservation from `RESERVED` to `RELEASED`: `reservedStock` decreases and
`availableStock` increases by the order quantity. The Order Service database
also records a `COMPLETED` row in `order_inventory_release_outbox` for each
order item.

Replaying the same Kafka event must not change the order or release stock a
second time. Temporarily stopping Inventory Service after a payment failure is
also a useful retry test: the outbox row remains `PENDING` with an increasing
`attempt_count`, then completes safely once Inventory Service is available.

Payment success does **not** deduct inventory again. The order already reserved
stock when it was created; a future fulfillment/shipment flow must call the
reservation-aware `DeductStock` operation to move `RESERVED` to `DEDUCTED`.

Order Service uses these defaults, which may be overridden per environment:

```yaml
order:
  inventory-release:
    fixed-delay-ms: 5000
    initial-delay-ms: 1000
    batch-size: 25
```

### Reservation-aware rollout order

Deploy Inventory Service (including `V2__add_inventory_reservations.sql`) before
deploying Order Service with `V4__add_inventory_reservations_and_release_outbox.sql`.
That order ensures Order Service never sends a `reservationId` to an older
Inventory Service that would ignore it. Existing `PENDING` or `CONFIRMED`
orders created before this deployment have no reservation IDs; audit and
manually remediate those orders before sending payment failures or cancellations
through the new automatic release flow.

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

This usually means persisted Kafka KRaft data no longer matches the current
local broker configuration.

For local development:

```powershell
docker compose stop kafka
docker compose rm -f kafka
docker volume rm ecommerce-platform_kafka-data
docker compose up -d kafka kafka-init
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
