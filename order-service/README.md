# Order Service

## What this service is

Order Service runs on port `8086` and owns checkout and the order lifecycle. It snapshots current catalog data, reserves inventory synchronously, records durable Kafka hand-offs, consumes payment outcomes, and compensates inventory through durable release work when an order fails, expires, is refunded, or is cancelled.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- Kafka producer/consumer
- gRPC client for Inventory Service
- Spring Security OAuth2 Resource Server
- Actuator, OpenAPI, Prometheus metrics

## Data owned

- Orders and immutable order items.
- Customer-scoped, payload-bound checkout idempotency records and processed payment-event inbox.
- Order-created, checkout-compensation, inventory-release, and refund-request outboxes with retry schedules.
- Lifecycle audit entries for cancellation, refund, and payment-system decisions.

## End-to-end flow

```text
Create order
  -> require Idempotency-Key and claim normalized request
  -> snapshot Product data and reserve Inventory with stable reservation IDs
  -> persist PENDING order, idempotency result, and order-created outbox atomically
  -> leased worker publishes order-created to Kafka

Payment outcome
  -> consume event once using persistent event ID inbox
  -> success confirms PENDING; failure/expiry queues durable release work
  -> confirmed cancellation or admin refund requests a durable Payment refund command
  -> workers retry Kafka publication and reservation-aware ReleaseStock safely
```

## Run locally

```bash
cd order-service
mvn spring-boot:run
```

Requires PostgreSQL, Kafka, Inventory gRPC, Config Server, and Auth issuer/JWK configuration.

## Documentation

Detailed integration and design documentation is in [`docs/`](docs/README.md):

- [API and contracts](docs/api.md)
- [High-level design](docs/hld.md)
- [Low-level design](docs/lld.md)
- [Data model](docs/schema.md)
- [Events and operations](docs/events-and-operations.md)
- [Checkout reliability contract](docs/checkout-reliability.md)
