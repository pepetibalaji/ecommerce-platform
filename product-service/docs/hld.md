# Product Service high-level design

## Responsibility and boundary

Product Service is the current catalog and price authority. It owns products, their seller owner, descriptive content, current `BigDecimal` price, availability flag, image URL references, and audit timestamps. Order/checkout consumers should resolve the current product through this boundary rather than trust cart-provided price data.

It does not own inventory quantity/reservations, checkout, purchase snapshots, payment, physical image upload/storage, price history, or search.

```text
Public client ----> Gateway ----> Product Service ----> MongoDB products
Admin / seller --- JWT ---------->       |
                                           +--> Kafka product-created (key: productId)
                                                    |
                                                    v
                                           Inventory Service: zero-stock provision
```

## Components and security

| Component | Role |
| --- | --- |
| `ProductController` | Public, admin, and seller REST endpoints. |
| OAuth2 resource server | JWT authentication and role enforcement. |
| `ProductService` | Validation boundary, UUID/time assignment, owner checks, persistence orchestration. |
| MongoDB / `ProductRepository` | Authoritative `products` collection and query indexes. |
| `ProductEventPublisher` | Asynchronously sends post-persistence creation events and records send failures. |
| Kafka | Delivers `product-created` to Inventory Service. |

Public `GET /products/**` is permitted. Admin routes require `ADMIN`; seller routes require `SELLER` or `ADMIN`. Seller mutation ownership uses JWT `userId`; an admin bypasses ownership checks for seller update/delete, but seller-list and seller-create still use the admin's own `userId`.

## Key flows

### Create

1. Controller authorizes role and validates body.
2. Service chooses owner (admin query `sellerId` or seller JWT `userId`), assigns UUID/timestamps, and defaults active to true.
3. Mongo saves the product.
4. Publisher asynchronously sends `product-created` keyed by product ID.
5. Response returns the saved catalog document immediately; Kafka send outcome does not change the HTTP result.

### Read and lifecycle

Public listing supports paging/category/range combinations and excludes explicitly inactive products. Admin has global administration. Sellers list their own products and cannot mutate another seller's product (reported as 404); product deletion is a hard delete. A direct product lookup may still return an inactive item so checkout can reject it explicitly.
