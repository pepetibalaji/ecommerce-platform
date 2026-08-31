# Inventory Service

## What this service is

Inventory Service owns stock and reservation state. It exposes REST on `8084` and gRPC on `9091`. Order Service uses gRPC to reserve and release stock with stable reservation IDs.

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
```

## Run locally

```bash
cd inventory-service
mvn spring-boot:run
```

Requires PostgreSQL, Config Server, and Auth issuer/JWK configuration.

## Current and next work

Current: stock REST APIs and reservation-aware gRPC. Next: low-stock threshold policy/event, seller/admin ownership resolution, and fulfilment-driven deduction.
