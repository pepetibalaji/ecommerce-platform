# Product Service

## What this service is

Product Service owns the catalog and runs on port `8082`. Customers can browse products; administrators manage catalog records.

## Technology

- Java 21, Spring Boot, Spring MVC
- MongoDB + Spring Data MongoDB
- Spring Security OAuth2 Resource Server
- MapStruct, OpenAPI, Actuator

## Data owned

MongoDB `product_db` stores products and catalog indexes. Other services must not write to this database.

## End-to-end flow

```text
Customer -> Gateway -> Product Service -> MongoDB -> product response
Administrator -> Gateway + ADMIN JWT -> Product Service -> validate/save product -> MongoDB
```

## Run locally

```bash
cd product-service
mvn spring-boot:run
```

Requires MongoDB, Config Server, and Auth issuer/JWK configuration.

## Current and next work

Current: catalog reads and administration. Next: authoritative checkout-time price validation, product lifecycle events, images, price history, and search indexing.
