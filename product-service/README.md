# Product Service

## What this service is

Product Service owns the catalogue and runs REST on `8082` plus an internal Product snapshot gRPC endpoint on `9092`. It is authoritative for product details, seller ownership, active status, and list price; Inventory is authoritative for stock and reservations.

## Technology

- Java 21, Spring Boot, Spring MVC
- MongoDB + Spring Data MongoDB
- Spring Security OAuth2 Resource Server
- MapStruct, OpenAPI, Actuator

## Data owned

MongoDB `product_db` stores products and catalog indexes. Other services must not write to this database.

## End-to-end flow

```text
Public customer -> Gateway -> Product Service -> MongoDB -> product response
Seller/admin -> Gateway + JWT -> Product Service -> validate/save -> MongoDB
                                                       -> Kafka product.lifecycle.v1 -> Inventory metadata synchronization
Inventory reconciliation -> Product snapshot gRPC -> Product Service authoritative lifecycle snapshot
```

## Run locally

```bash
cd product-service
mvn spring-boot:run
```

Requires MongoDB, Config Server, and Auth issuer/JWK configuration.

## Documentation

Detailed integration and design documentation is in [`docs/`](docs/README.md):

- [API and contracts](docs/api.md)
- [High-level design](docs/hld.md)
- [Low-level design](docs/lld.md)
- [Data model](docs/schema.md)
- [Events and operations](docs/events-and-operations.md)
