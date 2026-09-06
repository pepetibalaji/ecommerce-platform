# Product Service data model

Mongo database/collection ownership: `product_db.products` in the standard deployment. Other services must not write this collection.

## Product document

| Field | Mongo representation | Meaning |
| --- | --- | --- |
| `_id` / `id` | UUID stored as string | Service-generated product identity; Mongo's built-in unique index applies. |
| `sellerId` | UUID | Owning seller. Admin-created products require this value. |
| `active` | boolean | Defaults true; null legacy values behave as true in application reads. |
| `name` | string | Required API value. |
| `description`, `category`, `brand` | string | Optional catalog fields. |
| `price` | BSON `Decimal128` | Current positive unit price. Custom converters preserve `BigDecimal` semantics. |
| `imageUrls` | array of strings | HTTPS URL references; API permits no more than ten. |
| `createdAt`, `updatedAt` | local datetime | Service host timestamps. |

## Indexes

| Index | Fields | Purpose |
| --- | --- | --- |
| Mongo default | `_id` | Unique product lookup. |
| `seller_id_idx` | `sellerId` | Seller catalog pages (annotation-driven). |
| `category_idx` | `category` | Category filtering. |
| `price_idx` | `price` | Price-range filtering. |
| `category_price_idx` | `category`, `price` ascending | Combined category/range filtering. |

The index initializer ensures category, price, and compound indexes at startup. Product documents contain no inventory, order history, or immutable purchase-price snapshot.
