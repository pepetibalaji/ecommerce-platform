# Product Service events and operations

## `product-created` event contract

After each successfully persisted product creation, the service sends one `ProductCreatedEvent` to Kafka topic `product-created`, keyed by string `productId`. Its intent is zero-stock inventory provisioning; Inventory Service consumes it idempotently.

```json
{
  "eventId": "event UUID",
  "eventType": "PRODUCT_CREATED",
  "source": "product-service",
  "occurredAt": "2026-09-06T10:15:30Z",
  "correlationId": "product UUID",
  "traceId": null,
  "schemaVersion": "1.0",
  "productId": "product UUID",
  "sellerId": "seller UUID"
}
```

The product is already persisted when send begins. Send failures increment `product_created_event_publish_failures_total` and log the product ID; they do not roll back Mongo or change the successful create response. No producer-side outbox, durable retry, or replay endpoint exists.

## Configuration and observability

| Setting | Default | Effect |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Active Spring profile. |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Optional Config Server source. |
| `SPRING_KAFKA_BOOTSTRAP_SERVERS` | environment supplied | Kafka broker connection. |
| `OBSERVABILITY_LOG_FILE` | `../logs/product-service.json` | Structured log destination. |

MongoDB, JWT issuer/JWK, management exposure, Kafka serializer/deserializer, and tracing configuration come from the shared/config-server setup. Public utility endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, and `/swagger-ui.html`.

## Failure and recovery

* Mongo unavailable: reads/mutations fail; there is no cache fallback.
* Kafka unavailable: products can be created without a corresponding inventory event. Alert on `product_created_event_publish_failures_total` and logs, then reconcile affected product IDs by publishing the standard event through an approved operational mechanism.
* Bulk create: every product is saved before sends begin; some events can succeed while others fail.
* Deletion/update: neither produces an event, so downstream inventory/search lifecycle synchronization is not implemented.

Run `mvn spring-boot:run` from `product-service` with MongoDB, Kafka, and JWT/config dependencies available. Use [`api/product.http`](../../api/product.http) for local endpoint checks.
