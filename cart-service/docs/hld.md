# Cart Service high-level design

## Responsibility and boundary

Cart Service owns temporary cart composition for signed-in customers and anonymous visitors: owner identity, item IDs, product IDs, quantities, and the last update time. It intentionally does not validate products or own catalog, prices, availability, discounts, taxes, payments, checkout, or orders. Checkout must re-resolve those facts through the appropriate order/catalog/inventory boundaries.

```text
Browser / app
     | JWT customer requests / guest-cookie requests
     v
 Gateway -> Cart Service -> Redis
                  |        cart:{userId}
                  |        guest-cart:{guestId}
                  v
          Security -> Controller -> Service -> Repository

Checkout is outside Cart Service; Order Service must validate product, price, and stock.
```

## Components

| Component | Role |
| --- | --- |
| OAuth2 resource server | Authenticates customer routes and exposes JWT claims. |
| `CartController` | HTTP endpoints, request validation, identity extraction, guest cookie emission. |
| `CartService` | Item behavior, merge behavior, response mapping, distributed-lock orchestration. |
| `CartRedisRepository` | Redis reads/writes/deletes and TTL application. |
| Redis | The sole cart store and distributed-lock substrate. Redis loss or expiry loses carts by design. |
| Config Server | Optional local configuration source; deployed environments may override defaults. |
| Actuator / Prometheus / tracing | Health, metrics, logs, and tracing support. |

The current module has no database, Product/Inventory HTTP or gRPC client, Kafka producer, or Kafka consumer.

## Main flows

### Customer cart mutation

1. Security authenticates; controller reads JWT `userId`.
2. Service reads `cart:{userId}` (or creates it for add/read).
3. Service adds/increments, updates, or removes a line.
4. A non-empty changed cart is saved with the full customer TTL.
5. Removing the final line or clearing deletes the Redis key.

### Guest conversion after sign-in

1. Guest traffic receives/reuses an HttpOnly UUID cookie and uses `guest-cart:{guestId}`.
2. Merge locks the guest cart and destination customer cart.
3. Quantities are combined by product ID, then the customer cart is saved.
4. The guest cart is deleted only after destination save.

## Behavior guarantees and limits

* Saving refreshes TTL; reading an existing cart does not.
* Guest actions and merge use Redis locks. Customer CRUD currently has no customer lock, so concurrent read-modify-write operations may race.
* Returned carts are composition only; clients must not treat them as an order quote.
