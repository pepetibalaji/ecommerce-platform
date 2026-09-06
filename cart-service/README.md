# Cart Service

## What this service is

Cart Service runs on port `8085` and manages temporary authenticated-customer and anonymous guest carts in Redis. It stores only product IDs and quantities. It is intentionally separate from Order Service: a cart does not create an order and is not an authority for price, stock, or product validity.

## Technology

- Java 21, Spring Boot, Spring MVC
- Redis + Spring Data Redis
- Spring Security OAuth2 Resource Server
- OpenAPI and Actuator

## End-to-end flow

```text
Authenticated customer -> Gateway -> Cart Service -> cart:{userId}
Anonymous guest          -> Gateway -> Cart Service -> guest-cart:{guestId}
```

Customer carts expire after the configured inactivity period (7 days by default); guest carts expire after 30 days by default. A guest cart can be merged into the authenticated customer's cart after sign-in. The client sends a valid order request to Order Service when checkout begins, where catalog, price, and stock must be revalidated.

## Run locally

```bash
cd cart-service
mvn spring-boot:run
```

Requires Redis, Config Server, and Auth issuer/JWK configuration.

## Documentation

Detailed integration and design documentation is in [`docs/`](docs/README.md):

- [API and contracts](docs/api.md)
- [High-level design](docs/hld.md)
- [Low-level design](docs/lld.md)
- [Data model](docs/schema.md)
- [Operations](docs/events-and-operations.md)
