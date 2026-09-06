# Inventory Service events and operations

## Product-created consumer

The service consumes Product Service's `product-created` event using group `inventory-product-provisioner` by default. A valid event requires `productId`; it calls `createInitialInventory(productId, sellerId)`, creating `{availableStock:0, reservedStock:0}` only if no row exists. Duplicate delivery is a successful no-op due to the product lookup/unique key.

`@RetryableTopic` performs four attempts with initial 1-second delay and multiplier 2. Exhausted failures go to `product-created-dlq`. Successful processing increments `inventory_product_created_events_total` and logs product, seller, and event IDs.

## Configuration and observability

| Setting | Default | Effect |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile. |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Optional Config Server source. |
| `product-service.base-url` | `http://localhost:8082` | Product ownership verifier target. |
| `spring.kafka.consumer.product-created-group` | `inventory-product-provisioner` | Kafka consumer group. |
| `OBSERVABILITY_LOG_FILE` | `../logs/inventory-service.json` | Structured log destination. |

PostgreSQL, Flyway, Kafka, gRPC port, JWT, management, and tracing configuration are provided by shared/config-server environment configuration. REST utility endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html`.

## Operational risks and recovery

* PostgreSQL outage prevents reads, stock commands, reservation persistence, and provisioning.
* Kafka consumer failures must be monitored through lag, `product-created-dlq`, logs, and `inventory_product_created_events_total`. Fix the cause before replaying DLT records.
* Product-created only provisions stock; it does not synchronize product updates/deletes or seller changes.
* Product Service downtime can block non-admin seller REST authorization because ownership is checked remotely; admin routes bypass it.
* Never retry legacy gRPC mutations without `reservationId`; they can reserve/release/deduct repeatedly.

Run `mvn spring-boot:run` from `inventory-service` with PostgreSQL, Kafka, Product Service, and JWT/config dependencies available.
