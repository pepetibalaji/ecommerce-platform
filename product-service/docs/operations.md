# Product Service Operations

After a product is persisted, Product Service publishes `product-created` keyed by `productId`.
The version-1 payload contains `eventId`, `eventType=PRODUCT_CREATED`, `productId`, and `sellerId`.
Inventory Service consumes it to create a zero-stock inventory row.

Publication failures increment `product_created_event_publish_failures_total` and are logged with
the product ID. Reconcile by replaying the event after Kafka is restored. Required configuration
includes `SPRING_KAFKA_BOOTSTRAP_SERVERS` and OAuth resource-server/JWK settings.
