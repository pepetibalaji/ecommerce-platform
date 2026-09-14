# Order Service data model

PostgreSQL schema is managed by Flyway migrations `V1` through `V14`. Migration `V13` converts existing legacy timestamp columns with the explicit assumption that their prior values were UTC. New Order-side entities use `Instant` and PostgreSQL `TIMESTAMP WITH TIME ZONE`.

| Table | Key fields and invariants | Purpose |
| --- | --- | --- |
| `orders` | UUID ID; customer ID; immutable shipping fields; currency; lifecycle/payment fields; `payment_expires_at`; UTC timestamps | Authoritative purchase lifecycle record. Historic idempotency columns remain for compatibility; the dedicated record table is authoritative for current claims. |
| `order_items` | Order FK; product ID; immutable `product_name`, seller ID, snapshot price, quantity, reservation ID | Immutable purchase line. Reservation IDs link only to internal Inventory work and are never browser DTO fields. |
| `order_idempotency_records` | Unique `(user_id, idempotency_key)`; normalized request hash; optional order ID; `PROCESSING`/`COMPLETED`; expiry | Cross-instance, payload-bound checkout idempotency claim and replay record. |
| `order_created_outbox` | Unique order ID; `PENDING`/`PUBLISHED`/`FAILED`; attempt/schedule/error fields | Transactional hand-off for `order-created`. |
| `checkout_compensation_outbox` | Unique reservation ID; `PENDING`/`COMPLETED`/`FAILED`; attempt/schedule/error fields | Durable release work for reservations made by a checkout that could not complete. |
| `order_inventory_release_outbox` | Unique reservation ID; `PENDING`/`COMPLETED`/`FAILED`/`MANUAL_REVIEW`; reason and retry fields | Durable Inventory release after payment failure, expiry, cancellation, or full refund. |
| `order_refund_request_outbox` | Unique order ID; payment/customer/actor/reason/amount; `PENDING`/`PUBLISHED`/`FAILED` | Durable full-refund command to Payment Service. |
| `order_processed_events` | Unique payment event ID; type, order ID, processed time | Payment-event inbox/deduplication store. |
| `order_lifecycle_audit` | Order FK; action, actor ID/type, reason, optional refund request ID, UTC time | Application-append-only evidence for cancellation, refund, and payment-system decisions. |

## Important indexes and retention

- The unique `(user_id, idempotency_key)` constraint is the final protection against concurrent same-key checkout requests. The record’s expiry is indexed for cleanup and operations.
- Outbox tables are indexed by status, next-attempt time, and creation time where applicable, supporting leased worker scans.
- `order_created_outbox` and `order_refund_request_outbox` retain published and failed rows for reconciliation. They are not silently deleted after a terminal failure.
- Inventory-release rows are unique by reservation ID, making every release command idempotent. A `MANUAL_REVIEW` row denotes stock that may already have been deducted.
- Lifecycle audit rows are indexed by `(order_id, created_at)` for an ordered support history.

Historical rows created before immutable seller/name snapshots may contain null legacy snapshot fields and need controlled backfill before use in seller-facing views.
