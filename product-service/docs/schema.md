# Data Model

Mongo collection: `products`.

| Field | Type | Purpose |
| --- | --- | --- |
| `_id` | UUID string | Product identity. |
| `sellerId` | UUID | Seller owner for isolation. |
| `name`, `description`, `category`, `brand` | string | Catalog data. |
| `price` | Decimal128 | Current authoritative unit price. |
| `imageUrls` | list | Up to ten HTTPS image URLs. |
| `active` | boolean | False means unavailable to new orders. |
| `createdAt`, `updatedAt` | datetime | Audit timestamps. |

Seller ID, category, price, and category/price indexes support management and filtering. Legacy
documents with no active field are interpreted as active.
