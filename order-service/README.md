# Order Service

## What this service is

Order Service runs on port `8086` and owns the order lifecycle. It reserves inventory synchronously, publishes order events, consumes payment outcomes, and compensates inventory safely when an order fails or is cancelled.

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

## Current and next work

Current: order creation, payment outcome handling, idempotent inventory compensation. Next: transactional Kafka outbox for `order-created`, seller paid-order event enrichment, and Fulfilment integration.
