# Order Service documentation

Order Service is the checkout and purchase-lifecycle boundary. It owns orders, immutable product snapshots, shipping snapshots, payment outcome state, a processed-event inbox, and a durable inventory-release outbox.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Customer, seller, and admin REST endpoints and payloads. |
| [High-level design](hld.md) | Checkout ownership, dependencies, and event flows. |
| [Low-level design](lld.md) | Catalog/inventory coordination, state changes, idempotency, and compensation. |
| [Data model](schema.md) | PostgreSQL order, item, inbox, and outbox tables. |
| [Events and operations](events-and-operations.md) | Kafka contracts, release-worker recovery, metrics, and configuration. |
| [Current implementation](current-implementation.md) | Delivered behavior and known limits. |
