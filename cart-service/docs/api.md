# Cart Service API and contracts

Base path: `/api/v1/cart`. Requests and responses are JSON. This API never accepts or returns product names, prices, stock, discounts, or totals.

## Identity and access

Customer routes require a bearer JWT. The controller takes ownership only from the `userId` JWT claim—clients cannot select a user ID in a path, query, or body. A usable `userId` claim is therefore required.

Guest routes use the configurable `guestId` cookie (default name: `guestId`) and do not require a JWT. A missing or invalid cookie is replaced with a newly generated UUID, returned in `Set-Cookie`. Clients must retain and send this cookie.

| Route | Access | Owner |
| --- | --- | --- |
| `POST /`, `GET /`, `PUT /{itemId}`, `DELETE /{itemId}`, `DELETE /` | JWT required | JWT `userId` |
| `/guest`, `/guest/items/**` | Public | guest cookie UUID |
| `POST /merge-guest` | JWT required | customer plus guest identity |

## Shared payloads

### Add item

```json
{ "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2 }
```

| Field | Type | Requirement |
| --- | --- | --- |
| `productId` | string | Required UUID. Its existence is not verified by Cart Service. |
| `quantity` | integer | Required, at least `1`, and no more than the configured per-line limit (100 by default). |

Adding the same `productId` increments its existing line quantity.

## Idempotency and retries

`POST /api/v1/cart`, `POST /api/v1/cart/guest/items`, and `POST /api/v1/cart/merge-guest` accept an `Idempotency-Key` header. Cart Service stores the first successful response for 24 hours by default (`CART_IDEMPOTENCY_TTL`). Reusing a key with a different request returns `409 IDEMPOTENCY_KEY_CONFLICT`. Clients may retry lock-contention responses (`409 CART_LOCK_CONTENTION`) after the supplied `Retry-After` value, up to three attempts, using the same key.

### Update item

```json
{ "quantity": 5 }
```

`quantity` is required and must be at least `1`; this replaces, rather than increments, the line quantity.

### Cart response

```json
{
  "ownerType": "CUSTOMER",
  "ownerId": "11111111-1111-1111-1111-111111111111",
  "items": [{
    "itemId": "a8b7cc94-5e9b-40ac-8c22-5dc4b7f6f67c",
    "productId": "22222222-2222-2222-2222-222222222222",
    "quantity": 2
  }],
  "updatedAt": "2026-09-06T10:15:30.123Z",
  "version": 1
}
```

Guest carts use `ownerType: "GUEST"`. Timestamps are UTC `Instant` values. `version` increases on each persisted mutation.

## Customer endpoints

| Method and path | Behavior | Success |
| --- | --- | --- |
| `POST /api/v1/cart` | Creates a cart if needed; adds/increments a product line. | `200` cart |
| `GET /api/v1/cart` | Returns a read-only cart snapshot, or an empty cart if absent; does not acquire a mutation lock or write to Redis. | `200` cart |
| `PUT /api/v1/cart/{itemId}` | Replaces an existing line quantity. | `200` cart |
| `DELETE /api/v1/cart/{itemId}` | Removes a line. The last-line removal deletes the Redis key and returns an empty cart. | `200` cart |
| `DELETE /api/v1/cart` | Deletes the key whether it exists or not. | `200` empty body |

`itemId` is an opaque service-generated UUID and is resolved only inside the current owner's cart.

## Guest endpoints

Each route below emits a `Set-Cookie` header. The cookie is `HttpOnly`, scoped to `/api/v1/cart`, and uses configured `SameSite`, `Secure`, and TTL values.

| Method and path | Behavior | Success |
| --- | --- | --- |
| `POST /api/v1/cart/guest` | Creates or returns the guest cart. | `200` cart + cookie |
| `GET /api/v1/cart/guest` | Returns a read-only snapshot, or an empty cart if absent, without writing to Redis. Guest identity is still issued/refreshed in the cookie. | `200` cart + cookie |
| `POST /api/v1/cart/guest/items` | Creates if necessary, then adds/increments a line. | `200` cart + cookie |
| `PUT /api/v1/cart/guest/items/{itemId}` | Replaces an existing line quantity. | `200` cart + cookie |
| `DELETE /api/v1/cart/guest/items/{itemId}` | Removes a line and deletes the key if it was the final line. | `200` cart + cookie |
| `DELETE /api/v1/cart/guest` | Deletes the guest key. | `200` empty body + cookie |

Cart reads do not extend the Redis key TTL or contend with writes. An absent cart is
represented by an empty response with version `0`; the first add operation persists
it under the normal mutation lock. A read concurrent with a write sees a committed
snapshot before or after that write, never a partially written cart. Explicit guest
creation (`POST /guest`) and all mutations remain protected by the owner lock.

## Merge after login

`POST /api/v1/cart/merge-guest` requires a customer JWT and reads the guest identity exclusively from the HttpOnly cookie. The cookie is cleared after a successful merge. Matching product quantities are added, new products create a fresh customer line, the customer cart is saved, and then the guest key is deleted.

## Errors

| Condition | Status | Body |
| --- | --- | --- |
| Missing/invalid customer token | `401` | Spring Security response |
| Invalid body | `400` | JSON map: field name → validation message |
| Missing cart for item mutation or missing item | `404` | `ApiErrorResponse` |
| Invalid/mismatched merge guest ID | `400` | `ApiErrorResponse` |
| Guest/cart lock unavailable | `409` | `CART_LOCK_CONTENTION`, with `Retry-After: 1` |

`ApiErrorResponse` contains `timestamp`, `status`, `error`, `message`, and `path`; clients should not parse message text. OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
