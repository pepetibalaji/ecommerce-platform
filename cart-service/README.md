# Cart Service

## What this service is

Cart Service runs on port `8085` and manages temporary customer carts. It is intentionally separate from Order Service; a cart does not automatically create an order.

## Technology

- Java 21, Spring Boot, Spring MVC
- Redis + Spring Data Redis
- Spring Security OAuth2 Resource Server
- OpenAPI and Actuator

## End-to-end flow

```text
Authenticated customer -> Gateway -> Cart Service
  -> read/write Redis key cart:{userId}
  -> return current cart
```

Cart state expires after its configured inactivity period. The client sends a valid order request to Order Service when checkout begins.

## Run locally

```bash
cd cart-service
mvn spring-boot:run
```

Requires Redis, Config Server, and Auth issuer/JWK configuration.

## Current and next work

Current: customer cart CRUD and merge support. Next: checkout-time product/price validation and approved post-order cart clearing policy.
