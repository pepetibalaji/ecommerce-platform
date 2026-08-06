# Event-Driven E-Commerce Microservices Platform

Production-grade, cloud-native e-commerce backend built with **Java 21**, **Spring Boot 3**, **OAuth2**, **PostgreSQL**, **Redis**, **Kafka**, **gRPC**, **Docker**, **Spring Cloud Config**, and observability-first engineering.

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
| Observability            | Actuator, Micrometer, Prometheus, Grafana, Zipkin                    |
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
- Publishing Kafka domain events from transactional workflows
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
Order Service ---> Kafka topic: order-created

Storage:
Neon PostgreSQL ---> Auth, Product, Inventory, Order, Payment
Redis      ---> Cart, token blacklist, cache
Kafka      ---> Domain events
```

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
| Observability      | Actuator, Micrometer, Prometheus, Grafana, Zipkin                    |
| Build              | Maven Multi-Module                                                   |
| Local Infra        | Docker Compose                                                       |
| Future Deployment  | Kubernetes                                                           |

---

## Repository Structure

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

---

## Current Implementation Status

| Module               |                      Status | Highlights                                                |
| -------------------- | --------------------------: | --------------------------------------------------------- |
| Config Server        |                   Completed | Centralized runtime config                                |
| Auth Service         |    Completed core migration | OAuth2, JWT claims, refresh tokens, logout, admin users   |
| Product Service      |                   Completed | Product CRUD, filtering, pagination, admin APIs           |
| Inventory Service    |                   Completed | Stock management, gRPC APIs                               |
| Cart Service         |                   Completed | Redis-backed user cart                                    |
| Order Service        | Completed current milestone | Order lifecycle, Inventory gRPC, Kafka `order-created`    |
| Payment Service      |                   Completed | Payment processing and payment events                     |
| Notification Service |                     Planned | Kafka-driven notifications                                |
| Shipping Service     |                     Planned | Shipment assignment and tracking                          |
| Gateway Service      |           Planned / Pending | Routing, JWT validation, rate limiting, trace propagation |

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

Kafka topic:

```text
order-created
```

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

Current implemented topic:

```text
order-created
```

Planned topics:

```text
inventory-reserved
inventory-released
payment-success
payment-failed
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
Order Service ---> Payment Service
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

Database:

```text
product_db
```

Tables:

```text
products
product_images
```

### Inventory Service

Database:

```text
inventory_db
```

Table:

```text
inventory
```

### Order Service

Database:

```text
order_db
```

Tables:

```text
orders
order_items
```

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

Local infrastructure uses Neon Managed PostgreSQL and Upstash Redis by default.
Docker Compose provides Kafka and observability services; its PostgreSQL and
Redis containers are explicit local fallbacks.

| Component               |  Port |
| ----------------------- | ----: |
| Redis                   |  6379 |
| Zookeeper               |  2181 |
| Kafka internal listener |  9092 |
| Kafka host listener     | 29092 |
| Zipkin                  |  9411 |
| Prometheus              |  9090 |
| Grafana                 |  3000 |

Start infrastructure:

```bash
docker compose up -d
```

To use local PostgreSQL and/or Redis fallbacks instead, start the matching
profiles and update the runtime connection values in the environment file for
that environment:

```bash
docker compose --profile local-postgres up -d
```

```bash
docker compose --profile local-redis up -d
```

Create Kafka topic:

```bash
docker exec -it ecommerce-kafka kafka-topics \
  --bootstrap-server kafka:9092 \
  --create \
  --if-not-exists \
  --topic order-created \
  --partitions 3 \
  --replication-factor 1
```

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

---

## Swagger URLs

| Service           | Swagger UI                              |
| ----------------- | --------------------------------------- |
| Auth Service      | `http://localhost:8081/swagger-ui.html` |
| Product Service   | `http://localhost:8082/swagger-ui.html` |
| Inventory Service | `http://localhost:8084/swagger-ui.html` |
| Cart Service      | `http://localhost:8085/swagger-ui.html` |
| Order Service     | `http://localhost:8086/swagger-ui.html` |

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
| Inventory gRPC        |  9091 |
| PostgreSQL            |  5433 |
| Redis                 |  6379 |
| Kafka Host Listener   | 29092 |
| Kafka Docker Listener |  9092 |
| Zipkin                |  9411 |
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
| Zipkin               | Distributed tracing             |
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
mvn -pl auth-service,product-service,inventory-service,cart-service,order-service -am clean test
```

Current test coverage:

| Service           | Coverage                                                                           |
| ----------------- | ---------------------------------------------------------------------------------- |
| Auth Service      | Register, login, refresh, logout, user profile, admin user flows                   |
| Product Service   | Product create/read/delete, filtering, pagination                                  |
| Inventory Service | Inventory create/update/get                                                        |
| Cart Service      | Add/update/get/remove/clear cart                                                   |
| Order Service     | Create order, reserve stock, insufficient stock, cancel order, admin status update |

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
| 1        | Payment Service      | Process payments and publish payment success/failure events |
| 2        | Notification Service | Consume Kafka events and notify users                       |
| 3        | Shipping Service     | Shipment assignment and tracking                            |
| 4        | Address Service      | User address management                                     |
| 5        | Pricing Service      | Coupons and discounts                                       |
| 6        | Search Service       | Elasticsearch-powered product search                        |

### Platform Hardening

- Persistent RSA/JWK key management
- Refresh token hashing
- Access-token blacklist enforcement strategy
- API Gateway OAuth2 integration
- Kafka retry and DLQ configuration
- Outbox pattern for reliable event publishing
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
Production-grade event-driven e-commerce microservices platform with Spring Boot 3, OAuth2, PostgreSQL, Redis, Kafka, gRPC, Docker, Config Server, and observability.
```
