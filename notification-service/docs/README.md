# Notification Service documentation

Notification Service turns business and identity events into durable email notification work. It owns notification intents, recipient directory entries, preferences, delivery attempts, and Kafka event deduplication; it does not block checkout/payment workflows.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | User preference/history and admin investigation endpoints. |
| [High-level design](hld.md) | Event-to-email architecture, ownership, and dependencies. |
| [Low-level design](lld.md) | Event dedupe, preference handling, delivery/retry, auth action links. |
| [Data model](schema.md) | PostgreSQL notification entities and lifecycle. |
| [Events and operations](events-and-operations.md) | Topics, provider configuration, metrics, and recovery. |
| [Current implementation](current-implementation.md) | Implemented behavior and gaps. |
