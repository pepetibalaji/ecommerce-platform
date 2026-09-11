# Inventory Service events and operations

## Product lifecycle consumer

The primary contract is `com.ecommerce.common.events.product.ProductLifecycleEvent` on `product.lifecycle.v1`, keyed by product ID. It includes event ID, product/seller IDs, UTC occurrence time, event type, schema version, monotonically increasing product version, active state, actor, and a catalogue snapshot. Inventory applies schema version 1 and the created, updated, deactivated, reactivated, archived, and reconciled event types. The default consumer group is `inventory-product-lifecycle`.

Each snapshot is persisted using PostgreSQL `INSERT ... ON CONFLICT ... DO UPDATE WHERE inventory.product_version < incoming.product_version`. The unique product key arbitrates concurrent creates. The latest version and event ID are committed in the same transaction as metadata; duplicates, older retries, and a reconciliation snapshot already applied are no-ops even after a restart. A newer snapshot can provision a missing row without needing the original created event. Existing available/reserved stock is never reset by lifecycle delivery.

Deactivation and archival prevent new reservations. Existing reservation retries remain idempotent, and existing reservations can still be released or fulfilled. Reactivation permits reservations again when stock is available.

The listener retries four times with exponential delays (1s, 2s, 4s), then writes `product.lifecycle.v1-dlq`. Monitor Kafka lag, `inventory_product_lifecycle_events_total{result="applied|ignored"}`, and `inventory_product_lifecycle_dead_letters_total`. Invalid schemas or contradictory lifecycle states are rejected before writing inventory.

## Legacy product-created compatibility

For rolling upgrades the service still consumes `product-created` using group `inventory-product-provisioner`. It calls `createInitialInventory(productId, sellerId)`, atomically inserting `{availableStock:0, reservedStock:0}` with `ON CONFLICT DO NOTHING`. Duplicates, concurrent deliveries, and late legacy records cannot reset lifecycle state or stock.

`@RetryableTopic` performs four attempts with initial 1-second delay and multiplier 2. Exhausted failures go to `product-created-dlq`. Successful processing increments `inventory_product_created_events_total` and logs product, seller, and event IDs.

## Configuration and observability

| Setting | Default | Effect |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile. |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Optional Config Server source. |
| `product-service.base-url` | `http://localhost:8082` | Product ownership verifier target. |
| `spring.kafka.consumer.product-created-group` | `inventory-product-provisioner` | Kafka consumer group. |
| `spring.kafka.consumer.product-lifecycle-group` | `inventory-product-lifecycle` | Versioned lifecycle consumer group. |
| `OBSERVABILITY_LOG_FILE` | `../logs/inventory-service.json` | Structured log destination. |

PostgreSQL, Flyway, Kafka, gRPC port, JWT, management, and tracing configuration are provided by shared/config-server environment configuration. REST utility endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html`.

## Operational risks and recovery

* PostgreSQL outage prevents reads, stock commands, reservation persistence, and provisioning.
* Fix the cause of Kafka/PostgreSQL failures before replaying DLT records. Replay lifecycle records with their original product key and shared JSON type headers; duplicates or stale versions are harmless.
* For missed records, expired broker retention, or a rebuilt consumer, call Product Service's admin `POST /api/v1/admin/products/outbox/reconcile` in bounded pages using its `afterId` cursor and `size`. This republishes current `product.reconciled` snapshots through the transactional outbox. Follow every page and monitor applied/ignored counts and consumer lag. No stock counters are copied from Product.
* Legacy `product-created` only provisions stock. Current product state comes from `product.lifecycle.v1`; deploy the shared event contract and consumer before enabling the new producer, then reconcile existing products.
* Product Service downtime can block non-admin seller REST authorization because ownership is checked remotely; admin routes bypass it.
* Never retry legacy gRPC mutations without `reservationId`; they can reserve/release/deduct repeatedly.

Run `mvn spring-boot:run` from `inventory-service` with PostgreSQL, Kafka, Product Service, and JWT/config dependencies available.

The `ProductLifecyclePostgresKafkaIntegrationTest` exercises real Kafka delivery, Flyway/PostgreSQL upserts, missed creates, reordered/duplicate events, snapshot recovery, and concurrent provisioning. Images are configurable with `test.kafka.image` (default `apache/kafka:4.3.0`) and `test.postgres.image` (default `postgres:16-alpine`). Docker-dependent tests are skipped when Docker is unavailable; a skipped run is not integration verification.
