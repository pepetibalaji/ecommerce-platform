# Cart Service documentation

Cart Service is the Redis-backed, short-lived shopping-cart boundary. It stores only product IDs and quantities: it is not authoritative for products, prices, stock, discounts, checkout, payments, or orders.

| Cart owner | Identity | Redis key | Default TTL |
| --- | --- | --- | --- |
| Customer | JWT `userId` claim | `cart:{userId}` | 7 days |
| Guest | HttpOnly `guestId` UUID cookie | `guest-cart:{guestId}` | 30 days |

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Endpoints, payloads, cookies, security, validation, and errors. |
| [High-level design](hld.md) | Responsibilities, boundaries, dependencies, and flows. |
| [Low-level design](lld.md) | Code paths, merge behavior, storage, and locking. |
| [Data model](schema.md) | Redis keys, values, TTL, and lifecycle. |
| [Events and operations](events-and-operations.md) | Configuration, observability, failures, and local operation. |
| [Current implementation](current-implementation.md) | Delivered behavior and known limitations. |

Start with [API and contracts](api.md) for integration and [High-level design](hld.md) for the service boundary.
