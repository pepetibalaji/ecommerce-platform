# Event-Driven E-Commerce Platform

A Java 21 / Spring Boot microservices backend for an e-commerce platform. It uses REST at the edge, gRPC for inventory reservation, Kafka for domain events, and service-owned data stores.

## Architecture

```text
Client → API Gateway → domain services

Order Service ──gRPC──> Inventory Service
Order Service ──Kafka──> Payment Service
Payment Service ──Kafka──> Order Service and Notification Service
Auth Service ──Kafka──> Notification Service recipient directory
Notification Service ──SMTP/API──> Email provider
```

Each business service owns its data. Kafka delivery and email-provider outages do not block order or payment processing.

## How the platform works

1. A customer registers through Auth Service and receives an access token.
2. The customer browses products, manages a Redis-backed cart, and creates an order.
3. Order Service reserves inventory by gRPC and publishes `order-created` to Kafka.
4. Payment Service creates/updates payment state and publishes `payment-success` or `payment-failed`.
5. Order Service updates the order and releases stock when compensation is required.
6. Notification Service consumes transactional events, persists an email intent, and sends it without blocking the business workflow.

The frontend calls services through the API Gateway. Services validate Auth-issued JWTs. Internal workflows use gRPC or Kafka instead of direct synchronous chains.

## Services

| Service | Port | Responsibility | Storage |
| --- | ---: | --- | --- |
| Config Server | 8888 | Loads external environment configuration | Git-backed config repository |
| API Gateway | 8080 | Public API routing and JWT validation | — |
| Auth Service | 8081 | Registration, login, tokens, user lifecycle | PostgreSQL, Redis |
| Product Service | 8082 | Product catalog | MongoDB |
| Inventory Service | 8084 / 9091 | Stock and reservation ledger | PostgreSQL |
| Cart Service | 8085 | Customer carts | Redis |
| Order Service | 8086 | Order lifecycle and inventory compensation | PostgreSQL |
| Payment Service | 8087 / 9092 | Checkout, provider webhooks, refunds | PostgreSQL |
| Notification Service | 8088 | Transactional email intents, retries, recipient directory | PostgreSQL |

## Data and integration ownership

| Component | Owns | Main integration |
| --- | --- | --- |
| PostgreSQL | Auth, inventory, order, payment, and notification transactional records | Flyway migrations per service |
| MongoDB | Product catalog | Product Service REST API |
| Redis | Customer cart and token blacklist | Cart and Auth Services |
| Kafka | Domain events between services | `order-created`, payment outcomes, user contact updates |
| Mailtrap | Development/stage email delivery | Notification Service only |

## Key event flows

```text
Order created → Kafka → Payment processing
Payment success/failure → Kafka → Order update + customer notification
User registration/deactivation → Kafka → Notification recipient directory update
```

Notification Service stores each notification and delivery attempt before sending. It retries provider failures with exponential backoff and jitter. Recipient emails are stored locally from `user-contact-updated` events; no customer JWT is put on Kafka.

## Repository layout

```text
auth-service/            Identity and OAuth2/OIDC
gateway-service/         Public API gateway
product-service/         Catalog
inventory-service/       Stock and gRPC API
cart-service/            Redis cart
order-service/           Order workflow
payment-service/         Payments and refunds
notification-service/    Kafka-driven email delivery
common/                  Shared events, security, gRPC, exceptions
config-server/           Spring Cloud Config Server
monitoring/              Prometheus, Grafana, Tempo, Loki, Alloy
scripts/                 Local infrastructure helpers
docs/                    Detailed design and operational documentation
```

## Local development

Prerequisites: Java 21, Maven 3.9+, Docker Desktop, and the separate `ecommerce-config-repo`.

1. Start local infrastructure:

```bash
docker compose up -d
```

2. In the separate configuration repository, supply local database and Mailtrap Sandbox values in its ignored `dev/.env` file.

3. Start Config Server, then Auth Service, then the business services. The normal order is:

```text
Config Server -> Auth -> Product / Inventory / Cart -> Order -> Payment -> Notification
```

4. Run the test suite:

```bash
mvn -pl auth-service,product-service,inventory-service,cart-service,order-service,payment-service,notification-service -am clean test
```

Start Config Server first, then start the services from your IDE or with Maven. See [platform documentation](docs/README.md) and the README in each service directory for service-specific setup.

## Configuration and secrets

Runtime configuration lives in the separate `ecommerce-config-repo`; this repository contains code only. Store database passwords, Mailtrap credentials, API tokens, and other secrets in ignored local environment files or the deployment secret manager. Never commit secrets.

For development, Notification Service can use Mailtrap Sandbox. Stage uses Mailtrap Transactional Email with a verified sending domain.

## Testing and observability

```bash
mvn -pl notification-service -am test
mvn -pl auth-service,notification-service -am test
```

Metrics are exposed through Spring Boot Actuator and collected by Prometheus/Grafana. Logs and traces are configured under `monitoring/`.

## Documentation

- [Documentation index](docs/README.md)
- [Functional requirements](docs/functional-requirements.md)
- [Non-functional requirements](docs/non-functional-requirements.md)
- [High-level design](docs/high-level-design.md)
- [Low-level design](docs/low-level-design.md)

## Current scope

Implemented: account management, catalog, cart, inventory reservation, order/payment flow, refunds, Kafka workflows, and transactional notification infrastructure.

Planned: fulfilment event publishers, low-inventory notifications, seller-order enrichment, recipient-directory backfill for existing users, and stage email-domain setup.

## Contribution checklist

Before opening a pull request:

1. Keep service configuration and secrets in `ecommerce-config-repo`, not this repository.
2. Add Flyway migrations for relational schema changes.
3. Add/adjust unit and integration tests for changed workflows.
4. Update Kafka topic setup, monitoring, and documentation when adding an event-driven service or topic.
5. Run the affected Maven module tests locally.
