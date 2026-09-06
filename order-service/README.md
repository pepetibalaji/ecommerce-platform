# Order Service

## What this service is

Order Service runs on port `8086` and owns checkout and the order lifecycle. It snapshots current catalog data, reserves inventory synchronously, publishes order events, consumes payment outcomes, and compensates inventory through a durable release outbox when an order fails, is refunded, or is cancelled.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- Kafka producer/consumer
- gRPC client for Inventory Service
- Spring Security OAuth2 Resource Server
- Actuator, OpenAPI, Prometheus metrics

## Data owned

- Orders and order items.
- Processed payment-event inbox for idempotency.
- Inventory-release outbox and retry schedule.

## End-to-end flow

```text
Create order
  -> validate customer ownership/request
  -> call Inventory gRPC to reserve each item
  -> persist PENDING order and reservations
  -> publish order-created to Kafka

Payment outcome
  -> consume event once using eventId
  -> payment-success: confirm order
  -> payment-failed/cancelled: fail order and save inventory-release outbox work
  -> worker retries ReleaseStock until Inventory acknowledges
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
