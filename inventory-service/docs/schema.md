# Inventory Service data model

PostgreSQL owns the inventory schema. Flyway migrations V1–V3 create the following structures.

## `inventory`

| Column | Type/constraint | Meaning |
| --- | --- | --- |
| `id` | UUID primary key | Inventory row identity. |
| `product_id` | UUID, not null, unique | One inventory record per product. |
| `seller_id` | UUID, nullable | Added in V3; populated by product-created provisioning, not generic REST creation. |
| `available_stock` | integer, not null | Stock available to reserve. |
| `reserved_stock` | integer, not null | Stock currently held. |
| `updated_at` | timestamp, not null | Last service mutation time. |

Index: `idx_inventory_seller_id` on `seller_id`; the unique `product_id` constraint supplies lookup/indexing.

## `inventory_reservations`

| Column | Type/constraint | Meaning |
| --- | --- | --- |
| `id` | UUID primary key | Caller-provided stable reservation ID / idempotency key. |
| `product_id` | UUID, not null | Reserved product. |
| `quantity` | integer, positive check | Held quantity. |
| `status` | `RESERVED`, `RELEASED`, `DEDUCTED` check | Lifecycle state. |
| `created_at`, `updated_at` | timestamp, not null | Ledger audit timestamps. |

Index: `idx_inventory_reservations_product_id`. Reservations do not declare a database foreign key to inventory/product; consistency is enforced by service transaction logic.
