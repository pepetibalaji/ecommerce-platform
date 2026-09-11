# Product Service documentation

Product Service owns catalogue identity, eligible seller ownership, descriptive fields, list price/currency, active status, approved image references, and UTC timestamps. Inventory owns stock and reservations; checkout/order validation remains authoritative for final price and availability.

All catalogue mutations persist an immutable lifecycle snapshot in a Mongo transactional outbox. Inventory consumes versioned snapshots without resetting stock. Search and facets are provided by Product's bounded Mongo queries; no separate Search service is deployed.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Public, seller and admin routes; payloads, search/ranking, filters, stable paging and errors. |
| [Lifecycle and operations](lifecycle-operations.md) | Transaction requirements, deployment, migration, delivery/recovery, metrics and acceptance checks. |
| [High-level design](hld.md) | Service boundaries, dependencies and major flows. |
| [Low-level design](lld.md) | Validation, query implementation, transactions and worker fencing. |
| [Data model](schema.md) | Product/outbox fields, versions and indexes. |
| [Current implementation](current-implementation.md) | Implemented behavior and explicit limits. |
| [Events and operations](events-and-operations.md) | Entry point to the maintained lifecycle contract. |
