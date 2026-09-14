# Checkout reliability contract

`POST /api/v1/orders` requires an `Idempotency-Key` header. Keys are trimmed opaque strings up to 100 characters and are retained for 24 hours by default (`order.checkout.idempotency-retention`). A retry must reuse both the exact key and an equivalent checkout payload; reuse with a changed payload is rejected with `IDEMPOTENCY_KEY_REUSED`.

The checkout transaction persists the order and an `order_created_outbox` row atomically. A scheduled publisher leases pending rows with `FOR UPDATE SKIP LOCKED`, sends with the order ID as Kafka key, retries failures with backoff, and leaves terminal failures visible as `FAILED`. Consumers must remain idempotent because delivery is at-least-once.

Pending payment expires after `order.checkout.pending-payment-expiry` (15 minutes by default). The expiry worker locks eligible orders, changes them to `PAYMENT_EXPIRED`, and queues durable reservation-release work. Late payment events are recorded through the existing inbox and do not transition expired orders.

The public create request requires the full `shippingAddress` snapshot; `shippingAddressId` is not a public field. Order items expose immutable `productName`, `unitPrice`, and `lineTotal` snapshots. Customer lists enforce `page >= 0`, `1 <= size <= 50`, and default to `createdAt,desc`.

## Browser error contract

Checkout failures use a stable JSON envelope with `code`, `message`, `retryable`, `details`, and `traceId`. The browser must reuse the same idempotency key and payload for network failures and any error with `retryable: true`; a changed cart or address requires a new key. `IDEMPOTENCY_KEY_REUSED` is not retryable. Product and stock failures provide affected product IDs in `details`; insufficient stock also includes requested and available quantities.

The supported checkout codes are `IDEMPOTENCY_KEY_REQUIRED`, `IDEMPOTENCY_KEY_INVALID`, `IDEMPOTENCY_KEY_REUSED`, `CHECKOUT_ITEM_PRODUCT_NOT_FOUND`, `CHECKOUT_ITEM_PRODUCT_UNAVAILABLE`, `CHECKOUT_ITEM_INSUFFICIENT_STOCK`, `CHECKOUT_ITEM_QUANTITY_LIMIT`, `CHECKOUT_ORDER_QUANTITY_LIMIT`, `CHECKOUT_CATALOG_UNAVAILABLE`, and `CHECKOUT_INVENTORY_UNAVAILABLE`. Catalog and inventory-unavailable errors are retryable; availability and quantity errors are not.

## Cancellation and refunds

A customer can cancel a `PENDING` order, which records an audit entry and queues a durable inventory-release command. Cancelling a `CONFIRMED` order does **not** mark it cancelled or release stock immediately: the order moves to `REFUND_REQUESTED` and `order_refund_request_outbox` durably publishes a full-refund request to `payment-refund-requested`. A provider-confirmed `payment-refund-completed` event is the only path that transitions it to `REFUNDED` and queues a reservation release. Partial or unsafe outcomes are retained for fulfilment/manual review.

`POST /api/v1/admin/orders/{id}/refund-requests` is the command-oriented administrative path and requires an actor and reason. `GET /api/v1/admin/orders/{id}/audit` exposes immutable lifecycle audit entries to admins. The generic HTTP status mutation endpoint remains absent.

`GET /api/v1/admin/orders/reconciliation/outboxes` provides state counts for order-created, inventory-release, checkout-compensation, and refund-request outboxes. `PENDING`, `FAILED`, and `MANUAL_REVIEW` counts are the operational reconciliation signals; rows are never silently discarded after terminal failure.
