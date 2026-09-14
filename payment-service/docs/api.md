# Payment API and frontend contract

All paths below include their complete prefix. Customer APIs require a bearer JWT and use its `userId` for ownership. Admin APIs require `ADMIN`. Browser clients cannot create arbitrary Payment records.

| Method and path | Contract |
| --- | --- |
| `POST /api/v1/payments/orders/{orderId}/checkout-session` | Create or reuse one owned, unexpired checkout attempt after trusted Order validation. |
| `GET /api/v1/payments/orders/{orderId}` | Read the caller's authoritative Payment state. Missing preparation returns `PAYMENT_PREPARING`. |
| `GET /api/v1/payments/{paymentId}` | Read an owned payment, or `PAYMENT_NOT_FOUND`. |
| `GET /api/v1/payments/me?page=0&size=20` | Owned payment page. |
| `POST /api/v1/payments/orders/{orderId}/refresh` | Compatibility read of owned persisted state. It cannot contact a provider or confirm/fail a payment. |
| `GET /api/v1/admin/payments?status=&page=0&size=20` | Admin payment page, optionally filtered by state. |
| `GET /api/v1/admin/payments/{paymentId}` | Admin detail including attempts and refunds. |
| `POST /api/v1/admin/payments/{paymentId}/refund` | Return `202` after durably reserving refund work. |
| `POST /api/v1/admin/payments/{paymentId}/refunds/{refundId}/reconcile` | Return `202` after audited reconciliation of manual-review work: retrieve a known provider refund, or safely replay unknown acceptance while still inside the original 23-hour window. Body: `{"reason":"operator investigation reference"}`. |

Pagination requires `page >= 0` and `1 <= size <= 50`; invalid values return `400`. Sorting is always `createdAt DESC, id DESC`. Customer payment fields are `paymentId`, `orderId`, `userId`, `amount`, `currency`, `status`, `provider`, `createdAt`, and `updatedAt`. Internal failure text is not serialized. Timestamps are UTC/offset aware.

## Checkout and browser return

A successful checkout response contains only:

```json
{
  "paymentId": "c34caa2b-1eed-4d8f-972e-9b836ceac73c",
  "orderId": "bcf0ea26-aab5-4e76-a765-62a237af398e",
  "status": "REQUIRES_CUSTOMER_ACTION",
  "provider": "STRIPE",
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_example",
  "expiresAt": "2026-09-14T12:30:00Z"
}
```

Requests serialize on the Payment row. A committed attempt stores its unique provider idempotency key and immutable provider request parameters before provider execution. Retried/time-out requests replay that same attempt. Terminal, processing, cancelled, expired, refund-state, and cancellation-pending payments cannot start checkout.

Provider returns target `https://<approved-frontend-origin>/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}`. The backend validates exact approved HTTPS provider hosts before returning checkout URLs; the frontend also validates URLs and expiry.

On return, authenticate normally and poll `GET /api/v1/orders/{orderId}` and the Payment GET endpoint. A redirect/query parameter never proves payment. Current frontend polling is bounded to 25 checks over about two minutes, stops on navigation or a terminal outcome, and offers an explicit status retry when exhausted. Payment preparation allows at most four delayed retries; honor `retryAfterSeconds` with a small bounded delay. Disable duplicate redirect actions during the checkout request.

Legacy `GET /public/payments/success` and `/cancel` accept UUID `orderId` and `paymentId`, return `303` to the validated frontend route, and include `Deprecation: true`. They never mutate state.

## Stable errors

```json
{
  "code": "PAYMENT_PREPARING",
  "message": "Payment is being prepared. Please wait a moment.",
  "retryable": true,
  "retryAfterSeconds": 2,
  "traceId": "request-trace-id"
}
```

Use `code`, never message parsing. Retryable errors include a matching `Retry-After` header. Provider exception messages and secrets are never returned.

| Code | HTTP | Retryable |
| --- | --- | --- |
| PAYMENT_PREPARING | 404 | Yes, 2 seconds |
| PAYMENT_NOT_FOUND | 404 | No |
| PAYMENT_NOT_OWNED | 403 | No |
| PAYMENT_CHECKOUT_ALREADY_ACTIVE | 409 | Yes, 2 seconds; existing safe sessions are normally returned directly |
| PAYMENT_ALREADY_COMPLETED | 409 | No |
| PAYMENT_PROCESSING | 409 | Yes, 2 seconds; poll status |
| PAYMENT_EXPIRED | 409 | No |
| PAYMENT_CANCELLED | 409 | No |
| PAYMENT_PROVIDER_UNAVAILABLE | 503 | Yes, 2 seconds |
| PAYMENT_PROVIDER_CONFIGURATION_ERROR | 503 | No |
| PAYMENT_CHECKOUT_SESSION_EXPIRED | 409 | No |
| PAYMENT_REFUND_NOT_ALLOWED | 409 | No |
| PAYMENT_REFUND_IN_PROGRESS | 409 | Yes, 5 seconds |
| PAYMENT_REFUND_FAILED | 409 | No; contact support |
| PAYMENT_STATE_CONFLICT | 409 | Yes, 2 seconds; refresh status |
| PAYMENT_INVALID_REQUEST | 400 | No |
| PAYMENT_WEBHOOK_INVALID | 400 | No; invalid signature or event |

## Webhooks and refunds

`POST /api/v1/payments/webhooks/stripe` verifies `Stripe-Signature` against the exact raw body. Requests are size-limited and provider-rate-limited before processing. A unique `(provider, providerEventId)` inbox retains a payload hash and a sanitized verified envelope. Razorpay remains disabled. Sandbox is available only in explicit development/test profiles.

Only verified provider webhooks can confirm/fail a payment. Supported Stripe Checkout outcomes include immediate/delayed success, delayed failure, and explicit expiry. Terminal state cannot be overwritten by a late webhook; late success after cancellation/expiry/failure is flagged for review.

Admin refund body: `orderId`, positive `amount` with at most two decimal places, `currency`, optional `reason`, and `idempotencyKey`. Repeated keys must describe the same request. Successful, pending, and uncertain/manual-review amounts remain reserved to prevent over-refunds. Partial admin refunds are supported. Customer cancellation of a confirmed Order requests its entire immutable total; a partially refunded Order requires admin handling. A pending cancellation that races with payment success requests the remaining balance through the original cancellation worker. Refund provider execution and reconciliation run through durable work. See the [Order cancellation policy](../../order-service/docs/payment-lifecycle.md).

Legacy gRPC operations return `FAILED_PRECONDITION`; use these authenticated HTTP endpoints or the durable Order command contracts.

OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`. Operations endpoints and recovery procedures are in [the runbook](production-reliability.md).
