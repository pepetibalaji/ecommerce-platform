# Payment Service documentation

Payment Service owns payment, checkout-attempt, provider-webhook, and refund records. It turns `order-created` events into payments, creates provider checkout sessions, verifies provider callbacks, and publishes payment outcomes.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Customer, provider-webhook, public-return, and admin endpoints. |
| [High-level design](hld.md) | Ownership, provider adapters, events, and lifecycle. |
| [Low-level design](lld.md) | Checkout reuse, webhook idempotency, optimistic locking, refunds. |
| [Data model](schema.md) | PostgreSQL tables, uniqueness, indexes, and states. |
| [Events and operations](events-and-operations.md) | Kafka, provider configuration, monitoring, recovery. |
| [Current implementation](current-implementation.md) | Delivered behavior and limits. |
