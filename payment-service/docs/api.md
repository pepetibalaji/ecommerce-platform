# Payment Service API and contracts

Customer base path: `/api/v1/payments`; admin base path: `/api/v1/admin/payments`. Customer/admin endpoints require bearer JWT. Customer ownership comes from JWT `userId`; admin routes require `ADMIN` from `roles`, `role`, or `authorities` JWT claim. Webhooks and provider return pages are public.

## Customer endpoints

| Method and path | Behavior | Success |
| --- | --- | --- |
| `POST /payments/orders/{orderId}/checkout-session` | Owner starts or reuses a non-expired active provider checkout attempt. Payment must already have been prepared from `order-created`. | `200` checkout session |
| `GET /payments/me` | Pages only caller-owned payments. | `200` page |
| `GET /payments/orders/{orderId}` | Gets only caller-owned payment for order. | `200` payment |
| `GET /payments/{paymentId}` | Gets only caller-owned payment. | `200` payment |

Checkout response contains `paymentId`, `orderId`, status, provider, `checkoutUrl`, and expiry. An active `CREATED`/`REQUIRES_CUSTOMER_ACTION` attempt with future expiry is reused. New checkout is rejected for SUCCESS, PROCESSING, or refund/refunded payment states. Provider success and cancellation URLs must return the browser to the frontend's `/payment/return?orderId=&paymentId=` route; that screen reads authoritative Order and Payment API state.

## Provider and public endpoints

| Method/path | Access | Contract |
| --- | --- | --- |
| `POST /payments/webhooks/stripe` | Stripe | Raw payload plus required `Stripe-Signature`; adapter verifies it. |
| `POST /payments/webhooks/razorpay` | Razorpay | Raw payload plus optional `X-Razorpay-Signature`; adapter validation decides acceptance. |
| `GET /public/payments/success?orderId=&paymentId=` | Public | Legacy plain confirmation diagnostic; does not change payment state and must not be used as a provider return URL. |
| `GET /public/payments/cancel?orderId=&paymentId=` | Public | Legacy plain cancellation diagnostic; does not change payment state and must not be used as a provider return URL. |

Only a verified webhook changes payment state. Provider event IDs are unique per provider, so replayed callbacks are acknowledged without repeat processing.

## Admin endpoints

| Method/path | Behavior | Success |
| --- | --- | --- |
| `GET /admin/payments?status=&page=&size=` | Pages all payments, optionally by `PaymentStatus`. | `200` page |
| `GET /admin/payments/{paymentId}` | Retrieves payment detail. | `200` payment |
| `POST /admin/payments/{paymentId}/refund` | Requests provider refund. | `202` refund response |

Refund body contains order ID, positive amount (max two decimal places), 3-letter currency, optional reason, and idempotency key. Payment/order/currency must match; total nonfailed refunds cannot exceed payment amount. A repeated idempotency key returns/reuses its refund record.

Payment states: `PENDING`, `REQUIRES_CUSTOMER_ACTION`, `PROCESSING`, `SUCCESS`, `FAILED`, `CANCELLED`, `REFUND_REQUESTED`, `REFUND_PROCESSING`, `REFUNDED`, `REFUND_FAILED`. Common errors: missing resource `404`, wrong customer owner `401`, invalid state/request `400`, invalid webhook signature/provider error `4xx/5xx` as adapter throws, insufficient role `403`.

OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
