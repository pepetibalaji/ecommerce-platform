# Product Service

## What this service is

Product Service owns the catalog and runs on port `8082`. It is the current authority for product details, seller ownership, active status, and unit price. Customers can browse; sellers manage their own catalog; administrators can manage every product.

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
                                                       -> Kafka product-created -> Inventory provisioning
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
