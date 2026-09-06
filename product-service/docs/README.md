# Product Service documentation

Product Service is the MongoDB catalog boundary. It owns product identity, seller ownership, descriptive catalog fields, current unit price, availability flag, image URLs, and timestamps.

It does not own inventory counters, reservations, orders, payments, image storage, price history, or search indexing. On creation it emits an event for Inventory Service to provision a zero-stock record.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Public, seller, and admin endpoints; payloads, paging, access, and errors. |
| [High-level design](hld.md) | Boundaries, dependencies, security roles, and data/event flows. |
| [Low-level design](lld.md) | Controller, service, Mongo, ownership, mapping, and publication details. |
| [Data model](schema.md) | `products` document and indexes. |
| [Events and operations](events-and-operations.md) | `product-created` contract, configuration, monitoring, and recovery. |
| [Current implementation](current-implementation.md) | Implemented behavior and known limitations. |

Start with [API and contracts](api.md) for clients and [High-level design](hld.md) for service ownership.
