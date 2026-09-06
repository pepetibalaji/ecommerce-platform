# Product Service low-level design

## Layering

| Layer | Types | Work |
| --- | --- | --- |
| Web | `ProductController` | Maps `/api/v1`, validates request bodies, derives JWT owner/role. |
| Application | `ProductService` | Builds products, filters/lists, updates, hard deletes, enforces seller ownership. |
| Persistence | `ProductRepository` | Spring Data Mongo CRUD plus category/price/seller page queries. |
| Mapping | `ProductMapper` | Produces public DTO, normalizing null `active` to true and null images to `[]`. |
| Messaging | `ProductEventPublisher` | Sends `ProductCreatedEvent` after save. |

## Filtering and mutation

`getAllProducts` uses `PageRequest.of(page, size)` and selects repository queries only when category exists, both price bounds exist, or both exist. One-sided price bounds are ignored. Updates replace name, description, price, category, brand, and images; `active` changes only when non-null. A null image list becomes immutable empty list.

`getSellerOwnedProduct` first reads by ID. A non-admin seller whose UUID differs from `sellerId` receives `ResourceNotFoundException`, intentionally preventing ownership enumeration. Admin update/delete bypasses this check. There is no optimistic version field or write lock, so concurrent updates are last-write-wins.

## Creation event path

```text
save Product in Mongo
  -> construct ProductCreatedEvent(productId, sellerId, correlationId=productId)
  -> kafkaTemplate.send("product-created", productId, event)
  -> completion failure: increment metric and log product ID
```

Single creation saves before publishing. Bulk creation calls `saveAll` before looping over publications. The Kafka send is asynchronous and no outbox/transaction/retry is implemented in this service, so Mongo and Kafka are not atomically consistent.
