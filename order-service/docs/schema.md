# Order Service data model

PostgreSQL schema evolves through Flyway V1–V8.

| Table | Key fields | Purpose |
| --- | --- | --- |
| `orders` | UUID ID, user ID, total, currency, status, payment outcome fields, shipping snapshot, timestamps | Customer order lifecycle. |
| `order_items` | order FK, product ID, `product_name`, seller ID, quantity, snapshot price, inventory reservation ID | Immutable purchase lines; reservation ID has unique partial index. |
| `order_processed_events` | unique payment `event_id`, type, order ID, processed time | Payment consumer inbox/deduplication. |
| `order_inventory_release_outbox` | unique reservation ID, item/product/quantity, reason, status, attempt/error/schedule fields | Durable compensation command. |

`order_items` cascades on order deletion. Orders are indexed by user, status, currency, optional shipping address, update time, and optional payment ID; items by order, product, seller, and reservation. Outbox states are `PENDING`, `COMPLETED`, `FAILED`, `MANUAL_REVIEW`; reasons are `PAYMENT_FAILED`, `CANCELLED`, `FULL_REFUND`.

Historical rows created before V7/V8 can have null seller/product-name values and are not visible through seller views until controlled backfill.
