# Event-Driven E-Commerce Microservices Platform

Production-grade, cloud-native e-commerce backend built with **Java 21**, **Spring Boot 3**, **OAuth2**, **PostgreSQL**, **MongoDB**, **Redis**, **Kafka**, **gRPC**, **Docker**, **Spring Cloud Config**, and observability-first engineering.

This is not a simple CRUD demo. It is a distributed backend platform designed around real microservice architecture patterns: centralized configuration, OAuth2 security, service-to-service gRPC, event-driven Kafka workflows, Redis-backed cart storage, PostgreSQL-backed transactional services, shared platform modules, and production-readiness practices.

---

## Project Summary

| Area                     | Details                                                              |
| ------------------------ | -------------------------------------------------------------------- |
| Architecture             | Microservices, event-driven, domain-oriented                         |
| Language                 | Java 21                                                              |
| Framework                | Spring Boot 3.x                                                      |
| Security                 | Spring Security, OAuth2 Resource Server, Spring Authorization Server |
| Databases                | PostgreSQL, Redis                                                    |
| Messaging                | Apache Kafka                                                         |
| Internal Communication   | gRPC                                                                 |
| Configuration            | Spring Cloud Config Server                                           |
| Observability            | Actuator, Micrometer, Prometheus, Grafana, Tempo, Loki               |
| API Documentation        | Swagger / OpenAPI                                                    |
| Infrastructure           | Docker Compose                                                       |
| Future Deployment Target | Kubernetes                                                           |

---

## Why This Project Matters

This project demonstrates backend engineering skills required in real distributed systems:

- Designing service boundaries around business domains
- Migrating from custom JWT filters to OAuth2 Resource Server security
- Managing JWT identity propagation using stable claims
- Building gRPC contracts for internal communication
- Building Kafka-based workflows with idempotent consumers and dead-letter recovery
- Using Redis for user-scoped cart state
- Managing schema evolution with Flyway
- Centralizing runtime configuration through Config Server
- Debugging Kafka listener, serializer, and metadata issues
- Structuring reusable shared modules
- Writing controller and service tests for core flows
- Preparing services for observability and future Kubernetes deployment

---

## High-Level Architecture

```text
Frontend / Clients
        |
        v
API Gateway
        |
        v
REST APIs
        |
        +------------------+
        |                  |
        v                  v
Auth Service        Product Service
        |
        v

Resource Servers:
Product Service
Inventory Service
Cart Service
Order Service

Internal Sync Communication:
Order Service ---> Inventory Service via gRPC

Async Communication:
Order Service ---> Kafka topic: order-created ---> Payment Service
Payment Service ---> Kafka topics: payment-success / payment-failed ---> Order Service

Storage:
Neon PostgreSQL ---> Auth, Inventory, Order, Payment
MongoDB    ---> Product catalog
Redis      ---> Cart, token blacklist, cache
Kafka      ---> Domain events
```

For the implemented order, payment, and inventory lifecycle, including
protocol, sequence, state, data, retry, and rollout diagrams, see
[Order, Payment, and Inventory Saga Design](docs/order-payment-inventory-saga-design.md).

For a current service-by-service guide covering responsibilities, APIs, data
ownership, user flows, integrations, diagrams, and known limitations, see
[Service Documentation](docs/services/README.md).

---

## Technology Stack

| Layer              | Technology                                                           |
| ------------------ | -------------------------------------------------------------------- |
| Backend            | Java 21, Spring Boot 3.x                                             |
| Security           | Spring Security, OAuth2 Resource Server, Spring Authorization Server |
| Persistence        | PostgreSQL, Spring Data JPA, Flyway                                  |
| Cache / Fast State | Redis                                                                |
| Messaging          | Apache Kafka                                                         |
| Internal RPC       | gRPC, Protocol Buffers                                               |
| Config             | Spring Cloud Config Server                                           |
| API Docs           | Swagger / OpenAPI                                                    |
| Observability      | Actuator, Micrometer, Prometheus, Grafana, Tempo, Loki               |
| Build              | Maven Multi-Module                                                   |
| Local Infra        | Docker Compose                                                       |
| Future Deployment  | Kubernetes                                                           |

---

## Target Repository Structure

```text
ecommerce-platform
├── config-server
├── gateway-service
├── auth-service
├── product-service
├── inventory-service
├── cart-service
├── order-service
├── payment-service
├── notification-service         # Planned
├── shipping-service             # Planned
├── address-service              # Planned
├── pricing-service              # Planned
├── review-service               # Planned
├── search-service               # Planned
├── ai-service                   # Planned
├── common-security
├── common-exception
├── common-proto
├── common-grpc
├── common-events                # Evolving
├── common-utils                 # Evolving
└── common-logging               # Evolving
```

The current checked-in layout is smaller than the target shown above. Shared
modules live under `common/` (`common-events`, `common-exception`,
`common-grpc`, `common-logging`, `common-proto`, `common-redis`,
`common-security`, and `common-tracing`); planned services are tracked in the
roadmap rather than present as directories.

---

## Current Implementation Status

| Module               |                      Status | Highlights                                                |
| -------------------- | --------------------------: | --------------------------------------------------------- |
| Config Server        |                   Completed | Centralized runtime config                                |
| Auth Service         |    Completed core migration | OAuth2, JWT claims, refresh tokens, logout, admin users   |
| Product Service      |                   Completed | Product CRUD, filtering, pagination, admin APIs           |
| Inventory Service    |                   Completed | Stock management, gRPC APIs                               |
| Cart Service         |                   Completed | Redis-backed user cart                                    |
| Order Service        |                   Completed | Inventory reservation, payment outcomes, idempotent Kafka consumer, DLQ |
| Payment Service      |                   Completed | Checkout, verified provider webhooks, payment outcome events |
| Notification Service |                     Planned | Kafka-driven notifications                                |
| Shipping Service     |                     Planned | Shipment assignment and tracking                          |
| Gateway Service      |                   Completed | Routing, JWT validation, CORS, diagnostics, external route configuration |

---

## Completed Core Services

### Auth Service

Auth Service is the platform identity provider.

Responsibilities:

- User registration
- User login
- Access token generation
- Refresh token lifecycle
- Logout
- Redis-backed token blacklist
- User profile management
- Admin user management
- Force logout through token versioning
- OAuth2 / JWT claim generation

JWT claim contract:

```json
{
    "sub": "user@email.com",
    "userId": "uuid",
    "role": "CUSTOMER",
    "status": "ACTIVE",
    "tokenVersion": 0
}
```

Auth APIs:

| Method | Endpoint                          | Access        |
| ------ | --------------------------------- | ------------- |
| POST   | `/api/v1/auth/register`           | Public        |
| POST   | `/api/v1/auth/login`              | Public        |
| POST   | `/api/v1/auth/refresh`            | Public        |
| POST   | `/api/v1/auth/logout`             | Authenticated |
| GET    | `/api/v1/users/me`                | Authenticated |
| PUT    | `/api/v1/users/me`                | Authenticated |
| DELETE | `/api/v1/users/me`                | Authenticated |
| GET    | `/api/v1/admin/users`             | Admin         |
| GET    | `/api/v1/admin/users/{id}`        | Admin         |
| DELETE | `/api/v1/admin/users/{id}`        | Admin         |
| PUT    | `/api/v1/admin/users/{id}/role`   | Admin         |
| POST   | `/api/v1/admin/users/{id}/logout` | Admin         |

---

### Product Service

Product Service owns the product catalog.

Features:

- Product listing
- Product detail lookup
- Category filtering
- Price filtering
- Pagination
- Admin-only create/update/delete
- Swagger documentation
- OAuth2 Resource Server security

Product APIs:

| Method | Endpoint                                                     | Access |
| ------ | ------------------------------------------------------------ | ------ |
| GET    | `/api/v1/products`                                           | Public |
| GET    | `/api/v1/products/{id}`                                      | Public |
| GET    | `/api/v1/products?category=&minPrice=&maxPrice=&page=&size=` | Public |
| POST   | `/api/v1/admin/products`                                     | Admin  |
| POST   | `/api/v1/admin/products/bulk`                                | Admin  |
| PUT    | `/api/v1/admin/products/{id}`                                | Admin  |
| DELETE | `/api/v1/admin/products/{id}`                                | Admin  |

---

### Inventory Service

Inventory Service manages product stock and exposes gRPC APIs for internal service coordination.

Features:

- Available stock tracking
- Reserved stock tracking
- Reserve stock
- Release stock
- Deduct stock
- Inventory lookup
- Admin REST APIs
- gRPC server for Order Service integration

Inventory gRPC APIs:

```text
ReserveStock()
ReleaseStock()
DeductStock()
GetInventory()
```

Inventory is used by Order Service during checkout to reserve stock before the order is saved.

---

### Cart Service

Cart Service stores user carts in Redis.

Features:

- Add item
- Update item quantity
- Get current user's cart
- Remove item
- Clear cart
- Redis-backed storage
- JWT `userId` claim based cart ownership

Redis key pattern:

```text
cart:{userId}
```

Cart APIs:

| Method | Endpoint                | Access        |
| ------ | ----------------------- | ------------- |
| POST   | `/api/v1/cart`          | Authenticated |
| PUT    | `/api/v1/cart/{itemId}` | Authenticated |
| GET    | `/api/v1/cart`          | Authenticated |
| DELETE | `/api/v1/cart/{itemId}` | Authenticated |
| DELETE | `/api/v1/cart`          | Authenticated |

---

### Order Service

Order Service owns the customer order lifecycle.

Features:

- Create order
- Get current user’s orders
- Get order by ID
- Cancel order
- Admin order listing
- Admin status update
- Inventory gRPC integration
- Kafka `order-created` event publishing
- Kafka `payment-success` and `payment-failed` event consumption
- Idempotent payment outcome processing with an inbox table
- Retry and dead-letter handling for failed payment outcome processing
- Per-item idempotent inventory reservations and a durable release outbox for payment failure/cancellation
- JWT `userId` based order ownership
- OAuth2 Resource Server security

Order APIs:

| Method | Endpoint                             | Access        |
| ------ | ------------------------------------ | ------------- |
| POST   | `/api/v1/orders`                     | Authenticated |
| GET    | `/api/v1/orders`                     | Authenticated |
| GET    | `/api/v1/orders/{id}`                | Authenticated |
| GET    | `/api/v1/orders?status=&page=&size=` | Authenticated |
| PUT    | `/api/v1/orders/{id}/cancel`         | Authenticated |
| GET    | `/api/v1/admin/orders`               | Admin         |
| PUT    | `/api/v1/admin/orders/{id}/status`   | Admin         |

Order creation flow:

```text
Customer sends create order request
        |
        v
Order Service extracts userId from JWT
        |
        v
Order Service validates order items
        |
        v
Order Service calls Inventory Service through gRPC
        |
        v
Inventory reserves stock
        |
        v
Order is saved as PENDING
        |
        v
Order Service publishes order-created event to Kafka
```

Payment outcome flow:

```text
Payment Service verifies a provider webhook
        |
        v
Kafka publishes payment-success or payment-failed, keyed by orderId
        |
        v
Order Service consumes the outcome transactionally
        |
        +--> PENDING -> CONFIRMED on payment-success
        |
        +--> PENDING -> PAYMENT_FAILED on payment-failed
                     |
                     v
              Persist inventory-release outbox commands
                     |
                     v
              Retry idempotent reservation releases in Inventory Service
```

Duplicate deliveries are safely ignored through the `order_processed_events`
inbox table. Late outcomes never overwrite a `CONFIRMED`, `PAYMENT_FAILED`, or
`CANCELLED` order. Payment failure and cancellation release every item by its
stable reservation ID, so a worker retry cannot return the same stock twice.

---

## OAuth2 Security Migration

The platform migrated from custom JWT parsing to Spring OAuth2 Resource Server security.

### Previous Approach

```text
Custom JwtService
Custom JwtAuthenticationFilter
Manual Authorization header parsing
Manual JWT validation in each service
```

### Current Approach

```text
Auth Service = Authorization Server / token issuer
Product Service = Resource Server
Inventory Service = Resource Server
Cart Service = Resource Server
Order Service = Resource Server
```

Business services validate JWTs issued by Auth Service using:

```yaml
spring:
    security:
        oauth2:
            resourceserver:
                jwt:
                    issuer-uri: http://localhost:8081
                    jwk-set-uri: http://localhost:8081/oauth2/jwks
```

### Important Identity Fix

Original issue:

```java
UUID.fromString(authentication.getName())
```

Problem:

```text
authentication.getName() = email
```

Correct approach:

```java
UUID.fromString(jwt.getClaimAsString("userId"))
```

This fix was applied to user-owned flows such as Cart and Order.

---

## Shared Modules

| Module             | Purpose                                                          |
| ------------------ | ---------------------------------------------------------------- |
| `common-security`  | JWT claim helpers, authority conversion, Resource Server support |
| `common-exception` | Shared exceptions and API error responses                        |
| `common-proto`     | Shared gRPC protobuf contracts                                   |
| `common-grpc`      | Shared gRPC support                                              |
| `common-events`    | Shared event models and Kafka topic constants                    |
| `common-utils`     | Utility classes                                                  |
| `common-logging`   | Structured logging and trace helpers                             |

---

## Kafka Integration

Kafka is used for asynchronous event-driven workflows.

Locally bootstrapped topics:

```text
order-created
payment-success
payment-failed
order-dlq
```

Additional shared event contracts:

```text
inventory-reserved
inventory-released
order-completed
order-cancelled
shipment-created
notification-requested
```

For local development, Kafka runs in Docker while Spring Boot services run on the host machine.

Use this for host-running services:

```yaml
spring:
    kafka:
        bootstrap-servers: localhost:29092
```

Use this for services running inside Docker network:

```yaml
spring:
    kafka:
        bootstrap-servers: kafka:9092
```

Kafka producer config for Order Service:

```yaml
spring:
    kafka:
        bootstrap-servers: localhost:29092
        producer:
            acks: all
            key-serializer: org.apache.kafka.common.serialization.StringSerializer
            value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
            properties:
                spring.json.add.type.headers: false
```

Payment outcome consumer configuration lives in the sibling
`ecommerce-config-repo`. Payment Service includes JSON type headers for outcome
events, allowing Order Service to deserialize `PaymentSuccessEvent` and
`PaymentFailedEvent`. The default consumer group is
`order-service-payment-outcomes`; retryable failures are retried three times
and exhausted failures are sent to `order-dlq`.

### Payment Outcome Smoke Test

1. Create an order and keep its order ID.
2. Complete or fail checkout using the configured payment provider. A verified
   provider webhook—not the browser redirect—is the source of truth.
3. Inspect `payment-success` or `payment-failed`, then retrieve the order.
   Success changes `PENDING` to `CONFIRMED`; failure changes it to
   `PAYMENT_FAILED` and queues an inventory release. By default, the release
   worker retries every five seconds until Inventory Service acknowledges it.

```bash
docker exec -it ecommerce-kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic payment-success \
  --from-beginning
```

---

## gRPC Integration

gRPC is used for internal synchronous service-to-service communication.

Current implemented flow:

```text
Order Service ---> Inventory Service
```

Inventory gRPC operations:

```text
ReserveStock()
ReleaseStock()
DeductStock()
GetInventory()
```

Planned internal gRPC flows:

```text
Order Service ---> Shipping Service
```

---

## Database Design

### Auth Service

Database:

```text
auth_db
```

Tables:

```text
users
refresh_tokens
```

### Product Service

Collection:

```text
product_db
```

Document collection:

```text
products
```

### Inventory Service

Database:

```text
inventory_db
```

Table:

```text
inventory
inventory_reservations
```

`inventory_reservations` records a stable per-order-item ID and its
`RESERVED`, `RELEASED`, or `DEDUCTED` state. Reservation-aware release and
deduct requests are idempotent and lock the product inventory row while
updating stock.

### Order Service

Database:

```text
order_db
```

Tables:

```text
orders
order_items
order_processed_events
order_inventory_release_outbox
```

The `orders` table records the external payment ID, confirmation/failure
timestamps, and an optional payment failure reason. The Order Service never
uses a cross-service database foreign key to Payment Service.

`order_inventory_release_outbox` is persisted atomically with a payment-failed
or cancellation transition. Its retrying worker safely compensates inventory
after a restart or a temporary Inventory Service outage.

`CONFIRMED` means the payment has been verified. `order-completed` remains a
future fulfilment/delivery event, rather than the payment-confirmed state.

### Payment Service

Database:

```text
payment_db
```

Tables:

```text
payments
payment_attempts
payment_refunds
payment_webhook_events
```

Payment Service consumes `order-created`, creates checkout sessions, verifies
provider webhooks, and publishes `payment-success` or `payment-failed` keyed by
`orderId`. A transactional outbox for reliable outcome publication is planned.

### Cart Service

Storage:

```text
Redis
```

Key pattern:

```text
cart:{userId}
```

---

## Docker Compose Infrastructure

Docker Compose starts local PostgreSQL, Redis, Kafka, and observability
components. Stage/prod connection settings are supplied through runtime
configuration rather than assumed by this local Compose file.

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

Start infrastructure:

```bash
docker compose up -d
```

Inspect Kafka topics:

```bash
docker exec -it ecommerce-kafka kafka-topics \
  --bootstrap-server kafka:9092 \
  --list
```

`docker compose up -d` runs `kafka-init`, which creates `order-created`,
`payment-success`, `payment-failed`, and `order-dlq` automatically.

Consume Kafka events:

```bash
docker exec -it ecommerce-kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 \
  --topic order-created \
  --from-beginning
```

---

## Config Server

Services use Spring Cloud Config Server for runtime configuration. Secrets are
provided through ignored environment files at service runtime, not committed to
this repository or the Config Repository.

Local service `application.yml` files are intentionally minimal:

```yaml
spring:
    application:
        name: order-service

    profiles:
        active: dev

    config:
        import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

Environment-specific config files:

```text
application-dev.yml
auth-service-dev.yml
product-service-dev.yml
inventory-service-dev.yml
cart-service-dev.yml
order-service-dev.yml
payment-service-dev.yml
```

These environment-specific files are maintained in the sibling
`ecommerce-config-repo`, which Config Server loads at runtime. This repository
contains only the minimal bootstrap configuration for each service.

---

## Swagger URLs

| Service           | Swagger UI                              |
| ----------------- | --------------------------------------- |
| Auth Service      | `http://localhost:8081/swagger-ui.html` |
| Product Service   | `http://localhost:8082/swagger-ui.html` |
| Inventory Service | `http://localhost:8084/swagger-ui.html` |
| Cart Service      | `http://localhost:8085/swagger-ui.html` |
| Order Service     | `http://localhost:8086/swagger-ui.html` |
| Payment Service   | `http://localhost:8087/swagger-ui.html` |

---

## Local Service Ports

| Service               |  Port |
| --------------------- | ----: |
| Config Server         |  8888 |
| Auth Service          |  8081 |
| Product Service       |  8082 |
| Inventory Service     |  8084 |
| Cart Service          |  8085 |
| Order Service         |  8086 |
| Payment Service       |  8087 |
| Inventory gRPC        |  9091 |
| PostgreSQL            |  5433 |
| Redis                 |  6379 |
| Kafka Host Listener   | 29092 |
| Kafka Docker Listener |  9092 |
| Tempo HTTP API        |  3200 |
| Loki                  |  3100 |
| Prometheus            |  9090 |
| Grafana               |  3000 |

---

## Observability

The platform is designed for production-style observability.

| Tool                 | Purpose                         |
| -------------------- | ------------------------------- |
| Spring Boot Actuator | Health, metrics, runtime status |
| Micrometer           | Metrics instrumentation         |
| Prometheus           | Metrics scraping                |
| Grafana              | Metrics dashboards              |
| Tempo                | Distributed tracing             |
| Loki                 | Centralized structured logs     |
| Kafka Exporter       | Kafka consumer-group lag        |
| Structured Logging   | Trace-aware debugging           |

Common endpoints:

```text
/actuator/health
/actuator/info
/actuator/metrics
/actuator/prometheus
```

---

## Testing

Run tests for a single service:

```bash
cd auth-service
mvn clean test
```

Run selected services from root:

```bash
mvn -pl auth-service,product-service,inventory-service,cart-service,order-service,payment-service -am clean test
```

Current test coverage:

| Service           | Coverage                                                                           |
| ----------------- | ---------------------------------------------------------------------------------- |
| Auth Service      | Register, login, refresh, logout, user profile, admin user flows                   |
| Product Service   | Product create/read/delete, filtering, pagination                                  |
| Inventory Service | Inventory create/update/get, reservation-aware idempotent reserve/release/deduct |
| Cart Service      | Add/update/get/remove/clear cart                                                   |
| Order Service     | Create order, reservation-aware stock reserve, durable cancellation/payment-failure release outbox, payment success/failure transitions, duplicate and late event handling, payment outcome metrics |
| Payment Service   | Checkout, payment attempts, provider webhook handling, refunds, and payment outcome events |

---

## Engineering Problems Solved

### OAuth2 Resource Server Migration

The platform replaced custom JWT parsing with Spring Security Resource Server support.

Result:

- Auth Service owns token issuance.
- Business services validate tokens.
- JWT claim handling is consistent.
- Role mapping is centralized.
- Services no longer duplicate JWT parsing logic.

### Order Identity Bug

The original Order Service tried to parse:

```java
authentication.getName()
```

as a UUID. After OAuth2 migration, that value is the JWT subject, which is email.

Fix:

```java
jwt.getClaimAsString("userId")
```

### Kafka Producer Configuration

Order Service publishes `OrderCreatedEvent` as JSON to Kafka.

Fixes made:

- Host-running services use `localhost:29092`
- Docker-network services use `kafka:9092`
- Kafka value serializer changed to `JsonSerializer`
- `order-created` topic verified

### Shared Exception Handling

Business errors use shared exception classes instead of raw Java exceptions.

Examples:

| Exception                        | HTTP Status |
| -------------------------------- | ----------- |
| `ResourceNotFoundException`      | 404         |
| `ResourceAlreadyExistsException` | 409         |
| `UnauthorizedException`          | 401         |
| `BadRequestException`            | 400         |

### Stable Pagination

Order Service uses stable page serialization to avoid unstable `PageImpl` JSON output.

---

## Current Roadmap

### Next Services

| Priority | Service              | Purpose                                                     |
| -------- | -------------------- | ----------------------------------------------------------- |
| 1        | Notification Service | Consume domain events and notify users                      |
| 2        | Shipping Service     | Shipment assignment and tracking                            |
| 3        | Address Service      | User address management                                     |
| 4        | Pricing Service      | Coupons and discounts                                       |
| 5        | Search Service       | Elasticsearch-powered product search                        |

### Platform Hardening

- Persistent RSA/JWK key management
- Refresh token hashing
- Access-token blacklist enforcement strategy
- End-to-end OAuth/OIDC gateway routing and gateway security hardening
- Kafka retry and DLQ configuration for remaining consumers
- Transactional outbox for reliable Payment Service outcome publishing
- gRPC timeouts and circuit breakers
- Integration tests with Testcontainers
- CI/CD quality gates
- Kubernetes manifests
- Contract testing
- Chaos testing

---

## Future Target Architecture

```text
Frontend
   |
   v
Gateway
   |
   v
REST APIs
   |
   +--> Auth Service
   +--> Product Service
   +--> Cart Service
   +--> Order Service
   +--> Review Service
   +--> Pricing Service
   +--> Address Service

Internal gRPC:
Order --> Inventory
Order --> Payment
Order --> Shipping

Async Kafka:
OrderCreated
PaymentSucceeded
PaymentFailed
OrderCompleted
OrderCancelled
NotificationRequested

Storage:
PostgreSQL
Redis
MongoDB
Elasticsearch
```

---

## Recruiter / Reviewer Notes

This project demonstrates practical backend engineering in a distributed system:

- Java 21 and Spring Boot 3 microservices
- OAuth2 security migration from custom JWT filters
- Resource Server based authorization across services
- gRPC-based internal communication
- Kafka-based event-driven architecture
- Redis-backed user cart
- PostgreSQL-backed business services
- Flyway migrations
- Dockerized local infrastructure
- Config Server based environment management
- Swagger documentation
- Shared platform modules
- Testable service boundaries
- Production-readiness mindset

The project is structured to evolve into a Kubernetes-deployed, event-driven, cloud-native e-commerce platform.

---

## GitHub Description

```text
Production-grade event-driven e-commerce microservices platform with Spring Boot 3, OAuth2, PostgreSQL, MongoDB, Redis, Kafka, gRPC, Docker, Config Server, and observability.
```
