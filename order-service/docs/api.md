# Order Service API and contracts

Base path: `/api/v1`. Use the Gateway as the browser-facing origin. Every endpoint requires a bearer JWT; customer identity always comes from the JWT `userId` claim and cannot be selected by a request parameter. IDs are UUIDs and all API timestamps are UTC RFC 3339 instants (for example, `2026-09-14T10:15:30Z`).

The generated OpenAPI document is available at `/v3/api-docs` and Swagger UI at `/swagger-ui.html`. This guide records the lifecycle and retry semantics that cannot be inferred from a schema alone.

## Create an order

`POST /orders` requires an `Idempotency-Key` header and returns `201 Created` with the authoritative order representation.

```http
POST /api/v1/orders
Authorization: Bearer <access-token>
Idempotency-Key: 6ba7b814-9dad-11d1-80b4-00c04fd430c8
Content-Type: application/json
```

```json
{
  "currency": "INR",
  "shippingAddress": {
    "recipientName": "Asha Rao",
    "phone": "+919999999999",
    "line1": "10 Market Road",
    "line2": "Apartment 4B",
    "city": "Bengaluru",
    "state": "Karnataka",
    "postalCode": "560001",
    "country": "IN"
  },
  "items": [
    { "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2 }
  ]
}
```

`currency` is optional and defaults to the configured order currency (`INR` by default). It must be a three-letter ISO code. The full `shippingAddress` is required and is stored as an immutable order snapshot. There is no public `shippingAddressId` because this platform does not yet have an Address Service.

Each item needs a product ID and positive quantity. A deprecated `price` field may be accepted during a rolling client upgrade, but is ignored. The browser must never submit a trusted price, line total, seller ID, reservation ID, or order total. Order Service resolves the current catalogue snapshot and inventory itself.

### Idempotency rules

- The key is a trimmed opaque string of at most 100 characters and is scoped by customer.
- A key is retained for `order.checkout.idempotency-retention` (24 hours by default).
- Repeating the same key with the same normalized request returns the original order safely.
- Reusing the key with a changed cart, currency, or shipping snapshot returns `409 IDEMPOTENCY_KEY_REUSED`.
- Different customers may use the same value.
- Network and retryable failures must reuse the exact key and intended payload. A deliberate cart or address change must create a new key.

The idempotency claim is protected by a database uniqueness constraint and row locking. Concurrent same-key requests therefore do not create duplicate orders or extra inventory reservations.

## Order representation

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "userId": "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa",
  "totalAmount": 3998.00,
  "currency": "INR",
  "status": "PENDING",
  "paymentId": null,
  "paymentConfirmedAt": null,
  "paymentFailedAt": null,
  "paymentFailureReason": null,
  "cancelAllowed": true,
  "cancellationReasonCode": null,
  "createdAt": "2026-09-14T10:15:30Z",
  "updatedAt": "2026-09-14T10:15:30Z",
  "shippingAddress": {
    "recipientName": "Asha Rao",
    "phone": "+919999999999",
    "line1": "10 Market Road",
    "line2": "Apartment 4B",
    "city": "Bengaluru",
    "state": "Karnataka",
    "postalCode": "560001",
    "country": "IN"
  },
  "items": [
    {
      "id": "33333333-3333-3333-3333-333333333333",
      "productId": "22222222-2222-2222-2222-222222222222",
      "productName": "Wireless Headphones",
      "quantity": 2,
      "unitPrice": 1999.00,
      "lineTotal": 3998.00
    }
  ]
}
```

Item name, unit price, line total, address, and currency are immutable purchase snapshots. Reservation IDs and seller IDs are internal and are never returned in customer order responses. `cancelAllowed` and `cancellationReasonCode` are authoritative; clients must not infer cancellation eligibility from a status alone.

## Customer endpoints

| Method and path | Behavior |
| --- | --- |
| `POST /orders` | Performs idempotent checkout. It persists the order and durable `order-created` outbox work atomically. |
| `GET /orders?status=&page=0&size=10` | Returns only the caller's orders. `status` is an `OrderStatus`; `page` must be at least 0 and `size` must be 1 through 50. Results are ordered by `createdAt,desc`. |
| `GET /orders/{id}` | Returns only a caller-owned order; a missing or non-owned order is `404 ORDER_NOT_FOUND`. |
| `PUT /orders/{id}/cancel` | Cancels a pending order and queues durable reservation release. For a confirmed paid order it requests a full refund and returns `REFUND_REQUESTED`; it does not silently cancel or release stock. Retrying an already `REFUND_REQUESTED` order is safe. |

Lifecycle status values are `PENDING`, `CONFIRMED`, `PAYMENT_FAILED`, `PAYMENT_EXPIRED`, `CANCELLED`, `REFUND_REQUESTED`, `PARTIALLY_REFUNDED`, `REFUNDED`, and `REFUND_REQUIRES_FULFILMENT_REVIEW`.

Pending orders expire after `order.checkout.pending-payment-expiry` (15 minutes by default). Expiry moves the order to `PAYMENT_EXPIRED` and queues durable release work. Late or duplicate payment outcome events are recorded/deduplicated but cannot revive an expired or cancelled order.

## Seller and administrator endpoints

| Method and path | Access | Behavior |
| --- | --- | --- |
| `GET /seller/orders?page=0&size=10` | SELLER or ADMIN | Returns seller-scoped orders: only the JWT seller's lines and seller subtotal are included. It includes the fulfilment shipping snapshot. |
| `GET /admin/orders?status=&page=0&size=10` | ADMIN | Returns the administrative order list, optionally filtered by lifecycle status. It is a lifecycle read view, not a generic status-transition API. |
| `POST /admin/orders/{id}/refund-requests` | ADMIN | Requires `{ "reason": "..." }` (1–1000 characters). Requests a full refund only for a confirmed order and writes a durable Payment Service command plus lifecycle audit entry. Repeating a request while already `REFUND_REQUESTED` is safe. |
| `GET /admin/orders/{id}/audit` | ADMIN | Returns immutable lifecycle audit entries with actor, action, reason, linked refund-request ID, and UTC timestamp. |
| `GET /admin/orders/reconciliation/outboxes` | ADMIN | Returns state counts for order-created, inventory-release, checkout-compensation, and refund-request outboxes. |

There is intentionally no HTTP endpoint for generic admin status mutation. Payment/refund/fulfilment lifecycle changes must use the documented commands and events.

The reconciliation response has an `observedAt` timestamp and `pending`, `published`, `completed`, `failed`, and `manualReview` counts for each outbox family. A zero in a field means that state is not applicable to that particular outbox.

## Stable business-error contract

Checkout, order lookup, pagination, cancellation, and refund business failures use this envelope:

```json
{
  "code": "CHECKOUT_ITEM_INSUFFICIENT_STOCK",
  "message": "One or more items are no longer available in the requested quantity.",
  "retryable": false,
  "details": [
    {
      "productId": "22222222-2222-2222-2222-222222222222",
      "requestedQuantity": 2,
      "availableQuantity": 1
    }
  ],
  "traceId": "..."
}
```

`traceId` is empty when no trace context was established. Clients must branch on `code` and `retryable`, never on the message text. Framework-level malformed JSON and bean-validation errors retain the platform validation shape while error-envelope standardisation is completed across services.

| Code | HTTP status | Retryable | Meaning |
| --- | --- | --- | --- |
| `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_INVALID` | 400 | No | Supply a nonblank key of at most 100 characters. |
| `IDEMPOTENCY_KEY_REUSED` | 409 | No | Start a deliberate new checkout intent; do not retry automatically. |
| `CHECKOUT_REQUEST_INVALID`, `CHECKOUT_ITEM_INVALID_QUANTITY` | 400 | No | Correct the request. |
| `CHECKOUT_ITEM_PRODUCT_NOT_FOUND` | 404 | No | Refresh the catalogue/cart. |
| `CHECKOUT_ITEM_PRODUCT_UNAVAILABLE`, `CHECKOUT_ITEM_INSUFFICIENT_STOCK` | 409 | No | Refresh affected catalogue/cart data before a new intent. |
| `CHECKOUT_ITEM_QUANTITY_LIMIT`, `CHECKOUT_ORDER_QUANTITY_LIMIT` | 400 | No | Reduce quantities using the returned safe details. |
| `CHECKOUT_CATALOG_UNAVAILABLE`, `CHECKOUT_INVENTORY_UNAVAILABLE` | 503 | Yes | Preserve the exact checkout intent and retry it with the same key. |
| `ORDER_NOT_FOUND` | 404 | No | The order does not exist or is not owned by the caller. |
| `ORDER_CANCELLATION_NOT_ALLOWED`, `ORDER_STATE_CONFLICT` | 409 | No | Refresh the authoritative order and show its lifecycle state. |
| `PAGE_OUT_OF_RANGE`, `PAGE_SIZE_OUT_OF_RANGE` | 400 | No | Use documented pagination bounds. |
