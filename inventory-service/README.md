# Inventory Service

## What this service is

Inventory Service owns stock counters and reservation state. It exposes management REST on `8084` and protected Inventory gRPC on `9091`. Order Service uses stable reservation IDs to reserve, release, and deduct stock safely; customers never call Inventory directly.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- gRPC + Protocol Buffers
- Spring Security OAuth2 Resource Server
- Actuator, OpenAPI, structured logs

## Data owned

- Inventory quantity/availability per product.
- Reservation ledger, expiry recovery, stock-adjustment audit ledger, and durable event outbox.

## End-to-end flow

```text
Order creation
  -> Order Service calls ReserveStock(productId, quantity, reservationId)
  -> Inventory records or reuses reservation
  -> stock becomes reserved

Payment failure/cancellation
  -> Order release worker calls ReleaseStock with same reservationId
  -> Inventory releases stock once, even if request is repeated

Product lifecycle
  -> Product Service emits versioned Kafka snapshots
  -> Inventory provisions/synchronizes the row without resetting counters
  -> scheduled Product gRPC reconciliation repairs missed or retired rows
```

## Run locally

```bash
cd inventory-service
mvn spring-boot:run
```

Requires PostgreSQL, Config Server, and Auth issuer/JWK configuration.

## Documentation

Detailed integration and design documentation is in [`docs/`](docs/README.md):

- [API and contracts](docs/api.md)
- [High-level design](docs/hld.md)
- [Low-level design](docs/lld.md)
- [Data model](docs/schema.md)
- [Events and operations](docs/events-and-operations.md)
