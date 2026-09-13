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

Add finds an existing item by `productId`: it increments if found, otherwise makes a UUID `itemId`. Update and remove find by `itemId`. Update replaces quantity. Final-item removal deletes the cart key rather than persisting an empty cart. Customer and guest `GET` return a read-only snapshot, or an unpersisted empty cart on a miss; they do not acquire mutation locks or refresh the Redis TTL. Explicit guest creation persists an empty cart if needed. Update/remove require a cart and return `ResourceNotFoundException` when absent.

## Cookie and merge algorithm

Only UUID guest-cookie values are accepted. A missing/invalid value is replaced and emitted as a cookie. Cookie attributes come from `CartProperties` and the path is `/api/v1/cart`.

```text
lock cart-lock:guest:{guestId}
  lock cart-lock:customer:{userId}
    load guest; load or create customer
    for every guest item: append product or add quantity to matching product
    atomically save customer (with TTL), delete guest, and record merge response
  unlock customer
unlock guest
```

Guest item IDs are not retained when a new customer line is created; a fresh UUID is used. An absent/empty guest returns the customer cart unchanged. The merge identity replays the recorded response without adding quantities again.

## Concurrency, serialization, and time

Customer and guest mutations, explicit guest creation, and merge use `DistributedLockService`, random lock tokens, `RedisKeys.cartLock`, a 15-second duration, and `finally` unlock. Lock failure returns `409 CART_LOCK_CONTENTION` with `Retry-After: 1`. Reads bypass these locks and return a committed Redis snapshot before or after a concurrent write.

Redis values are read as `Cart` or converted through Jackson `ObjectMapper`. `updatedAt` uses UTC `Instant` values on create/change; it does not change on an existing-cart read. Legacy timezone-free cached timestamps are handled by the cart timestamp deserializer.
