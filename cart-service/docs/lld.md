# Cart Service low-level design

## Structure

| Layer | Types | Responsibility |
| --- | --- | --- |
| Web | `CartController` | Endpoint mapping, JWT/cookie ownership, validation, cookie headers. |
| Application | `CartService` | Create/read/mutate, merge algorithm, locks, DTO conversion. |
| Persistence | `CartRedisRepository` | Redis keys, values, TTL writes, deletes. |
| Model | `Cart`, `CartItem` | Serializable Redis value. |
| DTO | `AddCartItemRequest`, `UpdateCartItemRequest`, `MergeGuestCartRequest`, responses | HTTP contract. |

## Item algorithm

Add finds an existing item by `productId`: it increments if found, otherwise makes a UUID `itemId`. Update and remove find by `itemId`. Update replaces quantity. Final-item removal deletes the cart key rather than persisting an empty cart. Customer `GET` and guest create/read create an empty cart on a miss; update/remove require a cart and return `ResourceNotFoundException` when absent.

## Cookie and merge algorithm

Only UUID guest-cookie values are accepted. A missing/invalid value is replaced and emitted as a cookie. Cookie attributes come from `CartProperties` and the path is `/api/v1/cart`.

```text
lock cart-lock:guest:{guestId}
  lock cart-lock:customer:{userId}
    load guest; load or create customer
    for every guest item: append product or add quantity to matching product
    save customer (with TTL)
    delete guest
  unlock customer
unlock guest
```

Guest item IDs are not retained when a new customer line is created; a fresh UUID is used. An absent/empty guest returns the customer cart unchanged. Save-before-delete enables a retry after the completed delete.

## Concurrency, serialization, and time

Guest operations and merge use `DistributedLockService`, random lock tokens, `RedisKeys.cartLock`, a 15-second duration, and `finally` unlock. Lock failure throws `IllegalStateException`, currently handled as generic `500`.

Customer CRUD has no equivalent lock and is therefore not atomic under concurrent requests. Redis values are read as `Cart` or converted through Jackson `ObjectMapper`. `updatedAt` uses `LocalDateTime.now()` on create/change; it does not change on an existing-cart read.
