# Inventory Service documentation

Inventory Service is the PostgreSQL stock authority. It owns available/reserved counters and an idempotent reservation ledger per product. It exposes REST stock management, gRPC commands for Order Service, and Kafka-driven product inventory provisioning.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Admin/seller REST and Inventory gRPC contracts. |
| [High-level design](hld.md) | Service boundaries, dependencies, security, and flows. |
| [Low-level design](lld.md) | Pessimistic locks, reservation state transitions, ownership checks. |
| [Data model](schema.md) | PostgreSQL tables, constraints, migrations, and indexes. |
| [Events and operations](events-and-operations.md) | Product-created consumer, retries/DLT, metrics, configuration, recovery. |
| [Current implementation](current-implementation.md) | Implemented behavior and limitations. |
