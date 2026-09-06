# Inventory Service

## What this service is

Inventory Service owns stock counters and reservation state. It exposes REST on `8084` and gRPC on `9091`. Order Service uses reservation-aware gRPC commands with stable reservation IDs to reserve, release, and deduct stock safely.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- gRPC + Protocol Buffers
- Spring Security OAuth2 Resource Server
- Actuator, OpenAPI, structured logs

## Data owned

- Inventory quantity/availability per product.
- Reservation ledger with idempotent reservation state.

## End-to-end flow

```text
Order creation
  -> Order Service calls ReserveStock(productId, quantity, reservationId)
  -> Inventory records or reuses reservation
  -> stock becomes reserved

Payment failure/cancellation
  -> Order release worker calls ReleaseStock with same reservationId
  -> Inventory releases stock once, even if request is repeated

Product creation
  -> Product Service emits product-created
  -> Inventory creates a zero-stock record once, even if event delivery repeats
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
