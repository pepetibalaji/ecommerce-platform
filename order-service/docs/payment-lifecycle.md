# Payment lifecycle and trusted checkout

## Trusted Order lookup

Payment prepares an order once. The Order-created envelope must carry eventId, schemaVersion 1.0, source order-service, eventType ORDER_CREATED, UTC occurredAt, orderId, userId, a positive amount and ISO currency. A Kafka record key, when supplied, must equal orderId. Missing metadata is rejected rather than generated during deserialization. Only authorized Order infrastructure may produce these topics; broker authentication and ACLs remain required.

Before creation, Payment independently requests GET /internal/v1/payment-orders/{orderId}. The response contains only schemaVersion, orderId, userId, status, amount, currency, paymentId and paymentExpiresAt. Totals come from the persisted Order catalogue-price snapshot. The lookup never returns customer addresses or provider data.

Both services share PAYMENT_ORDER_LOOKUP_SECRET (at least 32 nonblank characters), bound to order.payment-lookup.secret and payment.order-lookup.secret. Payment uses payment.order-lookup.base-url; local default is http://localhost:8086, deployed configuration supplies ORDER_SERVICE_URI. Keep this endpoint private and use HTTPS outside local development.

X-Payment-Timestamp is UTC epoch seconds, accepted within 60 seconds. X-Payment-Signature is hex HMAC-SHA256 of GET, path, and timestamp separated by newline characters. The response X-Order-Signature covers path, request timestamp, and exact JSON body, also newline separated. Redirects are not followed, timeouts are bounded, and an absent/invalid signing key fails closed. Rotate both service keys together; there is no unsigned fallback.

Payment verifies exact Order/user IDs, decimal total and currency, PENDING state, no existing payment reference, and a future payment deadline. A repeated matching Order event returns its already prepared payment; inconsistent duplicates are rejected. For an existing durable cancellation/expiry command, preparation may also bind a CANCELLATION_REQUESTED or elapsed PENDING order so the cancellation worker can resolve it. Checkout is blocked while that command exists.

## Customer cancellation and refund policy

| Trigger | Order behavior | Inventory behavior |
| --- | --- | --- |
| Cancel a pending order | Persist a cancellation command and CANCELLATION_REQUESTED together | Retain reservation until authoritative Payment cancellation/expiry or full refund |
| Success races with pending cancellation | Bind paymentId, enter REFUND_REQUESTED, await the original Payment cancellation worker | No early release or fulfilment dispatch |
| Cancel a confirmed order | Persist a full immutable-total refund command and REFUND_REQUESTED together | Release only after full refund confirmation |
| Cancel a partially refunded order | Refuse automatic cancellation; admin investigates remaining balance | No automatic release |
| Full refund command races with a separate partial refund | Payment can refuse an unsafe full amount; Order enters review | No automatic release |
| Partial refund on confirmed order | PARTIALLY_REFUNDED | Retain stock/reservation; no full compensation |
| Provider refund fails or remains uncertain | REFUND_FAILED; operations reconcile the existing refund | Retain stock; successful full reconciliation may later queue release |
| Full refund after stock was deducted | Inventory release worker marks MANUAL_REVIEW | No automatic restock of deducted/shipped inventory |

Pending cancellation that discovers an already successful payment requests the remaining balance under the Payment row lock. The original cancellation command remains the sole owner; Order does not publish a second refund request when payment-success arrives. Actor ID, actor type, reason, correlation/trace IDs, command ID and UTC audit timestamps survive the durable handoff.

Each Order payment command is unique per (orderId, commandType): REFUND, CANCELLATION, EXPIRY. Repeated user actions return the existing command. The outbox publishes with bounded retry and row locking; exhausted publication is retained for operations. Expiry scanning excludes orders already holding an expiry command so unresolved orders cannot starve the queue.

## Payment outcomes and topic order

Payment outcomes lock Order and atomically record eventId in the processed-event inbox with the lifecycle/inventory outbox changes. They validate ownership and immutable totals. Duplicate event IDs have no effect; contradictory late success/failure/expiry cannot overwrite a terminal Order.

Only payment-expired changes PENDING or CANCELLATION_REQUESTED to PAYMENT_EXPIRED and queues inventory release. The Order deadline worker requests expiry from Payment; it does not independently release stock while a payment-success event may be delayed.

Kafka topics can be consumed out of order even when publication is ordered. A matching full-refund outcome can establish paymentId directly from PENDING and converge to REFUNDED without dispatching fulfilment. A full refund never downgrades REFUNDED, even under a distinct reconstructed event ID. A partial-refund outcome arriving before original payment-success is retryable; if it reaches order-dlq, replay that same event after confirming the original success was applied. Never manufacture a new event ID or manually mark a paid order cancelled.

REFUND_FAILED represents provider reconciliation work and may recover on verified full completion. REFUND_REQUIRES_FULFILMENT_REVIEW represents a separate fulfilment/policy conflict and is not automatically cleared by another event.

## Browser contract

The provider returns to /payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}. That query is informational. The authenticated frontend polls GET /api/v1/orders/{orderId} and GET /api/v1/payments/orders/{orderId}; the legacy refresh wrapper now also uses GET and cannot confirm payment.

Preparation calls checkout-session at most five times per user attempt and retries only code PAYMENT_PREPARING with retryable=true, respecting retryAfterSeconds clamped to 1-5 seconds. It disables duplicate work and redirects. Return polling stops after 25 checks (about 117 seconds of waiting), on terminal authorization errors, or navigation; manual refresh starts a new bounded query cycle.

A checkout link requires the exact HTTPS checkout.stripe.com host, no credentials/custom port, and a valid unexpired offset-aware expiresAt. Local Sandbox permits only http://localhost:3001/mock-checkout when the frontend development build sees provider SANDBOX. Visual mock mode may use its own /payment/return route; even mocks do not manufacture success on redirect. None of these local exceptions apply to production provider links.

See [Order command schema](payment-command.schema.json) and the [Payment outcome schema](../../payment-service/docs/payment-outcome.schema.json). New deployment migration: V15 adds typed Order payment commands and preserves provider outcome audit references.
