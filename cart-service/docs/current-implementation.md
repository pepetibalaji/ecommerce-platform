# Cart Service current implementation

## Implemented

* JWT-scoped customer cart CRUD at `/api/v1/cart` using `userId` claim ownership.
* Public guest cart, item CRUD, and clearing with HttpOnly UUID cookie ownership.
* Guest-to-customer merge with optional body fallback when an HttpOnly cookie cannot be forwarded.
* Product-line coalescing: adds and merges combine quantities by `productId`.
* Separate Redis TTLs for customer and guest carts.
* Distributed Redis locks for guest mutation and merge.
* Bean validation for non-blank product IDs and quantities at least one.
* OpenAPI, Actuator, structured logs, Prometheus, and tracing dependencies.

## Important limitations

* Product IDs are not validated against Product Service; stale or invalid IDs can be stored.
* No price, currency, stock, seller, discount, tax, or total is captured. Checkout must re-evaluate all of these.
* There is no checkout endpoint, event publication, or automatic post-order cart clearing.
* Customer mutations are not locked; concurrent mutations can lose updates. Guest mutations and merge are locked.
* State is Redis-only and expiring, not a durable wishlist or order record.
* Lock acquisition failures are generic `500`, not a dedicated retryable status.
* Guest cookie identity is browser-held bearer-style access; it is format-validated but not account-authenticated.

See [API and contracts](api.md) for exact integration behavior and [Events and operations](events-and-operations.md) for deployment considerations.
