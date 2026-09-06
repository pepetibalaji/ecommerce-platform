# Order Service API and contracts

Base path: `/api/v1`. Every API requires a bearer JWT. Customer ownership comes only from JWT `userId`; customer routes cannot choose another user. IDs are UUIDs.

## Create order

`POST /orders` returns `201 Created`.

```json
{
  "currency": "INR",
  "shippingAddressId": "11111111-1111-1111-1111-111111111111",
  "shippingAddress": {
    "recipientName": "Asha Rao", "phone": "+919999999999",
    "line1": "10 Market Road", "line2": null, "city": "Bengaluru",
    "state": "Karnataka", "postalCode": "560001", "country": "IN"
  },
  "items": [{ "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2 }]
}
```

`items` must be non-empty; every product ID and positive quantity is required. Client prices are never accepted. `currency` is optional (default configured as `INR`) but, if present, must be three letters; it is stored uppercase. `shippingAddress` is required even when `shippingAddressId` is supplied. Recipient, phone, line1, city, state, postal code, and two-letter country are required; country is uppercased. The service snapshots this address and catalog name/price/seller data.

Frontend checkout calls should send a unique `Idempotency-Key` header (maximum 100 characters) for each user checkout intent. Repeating the same key for the same user returns the existing order instead of creating another one. Calls without this header remain supported for existing clients but are not retry-safe.

## Order response

```json
{
  "id": "order UUID", "userId": "customer UUID", "totalAmount": 1998.00,
  "currency": "INR", "status": "PENDING", "paymentId": null,
  "paymentConfirmedAt": null, "paymentFailedAt": null, "paymentFailureReason": null,
  "createdAt": "2026-09-06T10:15:30", "updatedAt": "2026-09-06T10:15:30",
  "shippingAddress": { "addressId": "address UUID", "recipientName": "Asha Rao", "phone": "+919999999999", "line1": "10 Market Road", "line2": null, "city": "Bengaluru", "state": "Karnataka", "postalCode": "560001", "country": "IN" },
  "items": [{ "id": "item UUID", "productId": "product UUID", "quantity": 2, "price": 999.00 }]
}
```

The response intentionally does not expose internal seller IDs, product-name snapshots, or inventory reservation IDs. Time values are server-local `LocalDateTime` values without offsets.

## REST endpoints

| Method and path | Access | Behavior |
| --- | --- | --- |
| `POST /orders` | Authenticated customer | Performs checkout and creates PENDING order. |
| `GET /orders?status=&page=0&size=10` | Customer | Pages only the caller's orders; optional `OrderStatus`. |
| `GET /orders/{id}` | Customer | Returns only caller-owned order; other ownership is `404`. |
| `PUT /orders/{id}/cancel` | Customer | Cancels eligible own order and queues inventory release. |
| `GET /admin/orders?status=&page=0&size=10` | ADMIN | Pages all orders, optionally by status. |
| `PUT /admin/orders/{id}/status` | ADMIN | Changes status according to transition rules. Body: `{ "status": "CANCELLED" }`. |
| `GET /seller/orders?page=0&size=10` | SELLER or ADMIN | Pages orders containing the JWT seller's item rows and returns only that seller's lines and subtotal. |

Status values: `PENDING`, `CONFIRMED`, `PARTIALLY_REFUNDED`, `REFUNDED`, `REFUND_REQUIRES_FULFILMENT_REVIEW`, `PAYMENT_FAILED`, `CANCELLED`. Allowed direct transitions are PENDING → CONFIRMED/CANCELLED and CONFIRMED → CANCELLED; same-status is a no-op. Refund, payment-failed, and cancelled orders cannot be changed through this API. Cancelling queues one release command per reservation.

## Checkout behavior and errors

Before reservation, each product is fetched from Product Service. It must exist, be active, and provide seller ID, nonblank name, and price. Catalog unavailability fails closed. Inventory availability is checked, then each item is reserved through gRPC using a generated stable reservation UUID. On failure, attempted reservations are synchronously released best-effort.

Common errors: invalid request/catalog unavailable/insufficient stock/invalid transition → `400`; missing product/order or non-owned order → `404`; missing/invalid JWT → `401`; role violation → `403`. Validation failures are a JSON map of field names to messages; shared API errors use `timestamp`, `status`, `error`, `message`, `path`.

OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
