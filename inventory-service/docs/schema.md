# Inventory Service data model

Flyway migrations V1–V7 define the PostgreSQL schema. All operational timestamps are `TIMESTAMP WITH TIME ZONE` and application timestamps use UTC `Instant`.

| Table | Purpose | Important controls |
| --- | --- | --- |
| `inventory` | Current product counters and lifecycle metadata | Unique `product_id`; non-negative available/reserved checks; seller, active/version/event metadata; low-stock notification level/time. |
| `inventory_reservations` | Stable reservation ledger | UUID ID; positive quantity; `RESERVED`, `RELEASED`, `DEDUCTED`; `expires_at`; expiry index. |
| `inventory_reservation_audit` | Immutable reservation transition history | Reservation/product, from/to state, actor, UTC timestamp. |
| `inventory_stock_ledger` | Immutable management adjustment history | Before/after counters, adjustment, reason, actor, seller, reference, UTC timestamp. |
| `inventory_event_outbox` | Durable outgoing Kafka events | Topic/key/JSON payload; `PENDING`, `PROCESSING`, `PUBLISHED`, `DEAD`; retry/lease metadata. |

`available_stock + reserved_stock` represents unsold physical stock. Product lifecycle and reconciliation updates never overwrite either counter.

## Stock adjustment reasons

`STOCK_RECEIVED`, `STOCK_CORRECTION`, `DAMAGE`, `RETURN`, and `MANUAL_RECONCILIATION` are enforced by the ledger constraint.
