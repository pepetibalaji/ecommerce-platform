# Frontend integration contract

Use Gateway as the only browser API origin. Do not call individual service ports from the frontend.

## Environment

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_USE_MOCKS=false
VITE_SESSION_IDLE_TIMEOUT_MS=1800000
VITE_SESSION_WARNING_MS=300000
```

For local development the Gateway CORS default permits `http://localhost:3000`, `http://localhost:4200`, and Vite's `http://localhost:5173`. Hosted stage/production deployments must set `GATEWAY_CORS_ALLOWED_ORIGINS` to the exact frontend origin. Browser API calls include `credentials: "include"` for both the guest-cart and Auth refresh HttpOnly cookies. Gateway CORS must allow `Idempotency-Key`, `Authorization`, and `Content-Type`, and expose `Retry-After` so the browser can read retry guidance.

`VITE_USE_MOCKS=true` is supported only for visual development with `npm run dev`.
The Vite configuration rejects it for every build and for production mode. The
example defaults to real Gateway requests; set `VITE_USE_MOCKS=false` explicitly in
release environments. Frontend mock checkout returns never manufacture a paid order.

Only public frontend values belong in the frontend environment/build configuration. Never include Config Server URLs, backend service ports, vault paths, databases, Kafka/Redis settings, internal service tokens, OAuth/JWT secrets, or payment/email provider credentials. Before a stage/production deployment, verify that the exact frontend origin matches Gateway CORS, payment-provider return URLs, and Auth/Notification email-link origins.

## Customer path

1. Browse `GET /api/v1/products`; list responses exclude inactive products.
2. Use `/api/v1/cart/guest/**` before authentication. After login, call `POST /api/v1/cart/merge-guest` with browser credentials.
3. Send bearer token on authenticated requests.
4. For every checkout intent, create a UUID in the browser and send it as `Idempotency-Key` on `POST /api/v1/orders`. Reuse that key only for a retry of the exact same action. Send only `productId` and `quantity` for each item; a legacy `price` field may be tolerated during rollout but is ignored. The successful Order response contains the authoritative snapshot prices and total.
   A network failure or a structured error with `retryable: true` retains the exact key and payload. A changed cart, currency, or address must create a new checkout intent/key. `409 IDEMPOTENCY_KEY_REUSED` is never automatic-retryable: explain the conflict and require a deliberate new attempt.
   Checkout business errors include stable `code`, `retryable`, `details`, and `traceId` fields. For `CHECKOUT_ITEM_*` errors, refresh the cart/catalogue display and ask the customer to review the identified item before starting a new attempt. `CHECKOUT_CATALOG_UNAVAILABLE` and `CHECKOUT_INVENTORY_UNAVAILABLE` are the retryable dependency failures.
5. Payment preparation follows asynchronous `order-created`. Call `POST /api/v1/payments/orders/{orderId}/checkout-session` for the created Order. Automatically retry only the stable `PAYMENT_PREPARING` code with `retryable: true`, at most five attempts and one-to-five-second bounded delays. An arbitrary `404` is not proof of pending preparation. Payment Service owns the reserved provider session and idempotency key; the browser never supplies amounts, provider keys, or a replacement session.
6. Accept a checkout redirect only to the exact HTTPS `checkout.stripe.com` host, without credentials or a nondefault port, with an unexpired timezone-aware `expiresAt`. Disable repeat redirect clicks. The local backend Sandbox URL `http://localhost:3001/mock-checkout` is development-only; no browser sandbox payment-completion app or public Sandbox webhook is provided.
7. The `/payment/return` page polls authenticated `GET /api/v1/orders/{orderId}` and `GET /api/v1/payments/orders/{orderId}` up to 25 times over about two minutes. It never calls a provider-confirmation mutation. Provider URL parameters and browser timers cannot establish success. Recorded Order/Payment state is authoritative, and a manual refresh starts another bounded polling run after automatic checks stop.

## Payment, cancellation, and refund states

Payment `EXPIRED` and Order `PAYMENT_EXPIRED` end the existing checkout intent.
Show the recorded failure/expiry and send the customer back to review the cart;
never revive the old session. Display `CANCELLATION_REQUESTED` as pending while
Payment resolves the existing charge. Paid cancellation moves through
`REFUND_REQUESTED`; it must not appear as immediately cancelled or refunded.

`PARTIALLY_REFUNDED` and `REFUNDED` represent recorded refund outcomes.
`REFUND_FAILED` and `REFUND_REQUIRES_FULFILMENT_REVIEW` need support/operator review.
Stock release follows the authoritative backend outcome, never a browser action.
Customer cancellation and admin full-order refund use Order APIs; remaining-balance
partial refunds and provider reconciliation are operator workflows. See the
[Payment recovery runbook](../payment-service/docs/production-reliability.md).

Payment errors expose safe `code`, `message`, `retryable`, `retryAfterSeconds`, and
`traceId` values. Prefer the JSON retry delay, with exposed `Retry-After` as a
fallback, and do not show raw provider failures. Timestamps are ISO-8601 with a UTC
offset; display them in the user's timezone without treating local time as proof
of expiry. Payment list pagination is bounded to 50 records per page; the current
admin screen requests 20.

## Browser sessions

Auth responses provide an access token kept only in memory. Refresh secrets are
issued/rotated by Auth in an HttpOnly cookie and never returned to JavaScript or
stored in localStorage/sessionStorage. Refresh and logout POSTs use the cookie,
without a refresh-token JSON body. On app startup, restore via refresh before
resolving protected routes. Recent activity permits silent access renewal; an
authenticated 401 joins a single-flight refresh and gets at most one retry.
Failed refresh clears local authentication.

The frontend defaults to a 30-minute inactivity countdown with a five-minute
Continue session / Sign out warning. Match frontend values to backend policy;
Auth's idle and absolute expiry remain authoritative. Stage/prod cookies are
Secure and require HTTPS plus compatible SameSite/CORS deployment settings.

## Seller and administrator paths

Seller pages call only `/api/v1/seller/**`; administrator pages call only
`/api/v1/admin/**` (plus the operator-only Notification diagnostics route).
Use roles for navigation only: the Gateway and downstream service permission
checks are authoritative. The current contracts deliberately do not support an
admin catalogue/inventory directory, seller fulfilment actions, seller metrics,
or a customer notification inbox, so the frontend shows known-ID or read-only
boundaries instead of calling invented endpoints.

Seller bulk import uses `POST /api/v1/seller/products/bulk` for 1..100 products,
with ownership taken from JWT. Managed editors load
`GET /api/v1/seller/products/{id}` or `/api/v1/admin/products/{id}`, including
inactive records. DELETE archives rather than removes history; setting active
true in a full update reactivates an eligible seller's product. Admin catalogue
recovery offers confirmed dead-letter replay and bounded cursor reconciliation
via `/api/v1/admin/products/outbox/**`; no refresh secret or internal Auth token
is exposed by these actions.

Payment Service serializes payment identity as `paymentId`; the frontend client
normalizes that wire field internally. For an Order-linked administrative refund,
the browser must call `POST /api/v1/admin/orders/{orderId}/refund-requests` with
a required human reason, not the direct Payment refund endpoint. The Order command
creates the lifecycle audit entry and durable Payment refund request; a request is
not proof that the provider refund completed. Admin user status PATCH returns no
body, so refresh the known user record after a successful update.

## API client rules

* Generate TypeScript types from Gateway OpenAPI routes where practical (`/product/v3/api-docs`, `/cart/v3/api-docs`, and so on).
* Handle `400` field errors from `ApiErrorResponse.fieldErrors`, with compatibility for legacy validation maps and Problem Details until errors are unified platform-wide.
* On authenticated `401`, attempt one shared cookie-backed refresh and one retry; failed refresh clears the session and sign-in is required. On `403`, show access denied. On `429`, respect `Retry-After` and prevent repeated submission. On retryable Gateway `503`/`504`, preserve safe input and provide bounded retry without exposing internal-service details.
* Never derive price, stock, or authorization from cart data in the browser; Product/Order/Payment responses are authoritative.
* Checkout limits default to 100 units per product and 500 total units. Operations can override them with `order.checkout.max-quantity-per-product`, `order.checkout.max-total-quantity`, and `order.checkout.product-maximum-quantities.<product-uuid>`.
* Render `EXPIRED` / `PAYMENT_EXPIRED` as terminal for the checkout intent, and `CANCELLATION_REQUESTED` / `REFUND_REQUESTED` as in-progress. Refresh the authoritative Order/Payment state; never infer completion from a provider return page, refund-request acknowledgement, or a browser timer.

## Required deployment variables

| Environment | Required frontend-related setting |
| --- | --- |
| Dev | `VITE_API_BASE_URL=http://localhost:8080`; `VITE_USE_MOCKS=false` for real Gateway requests. |
| Stage/Prod frontend | Public HTTPS Gateway `VITE_API_BASE_URL` and `VITE_USE_MOCKS=false`; `/payment/return` must serve the frontend application (Vercel rewrite is included). |
| Stage/Prod Gateway | `GATEWAY_CORS_ALLOWED_ORIGINS=https://your-frontend.example` |
| Stage/Prod Payment | `PAYMENT_FRONTEND_ORIGINS=https://your-frontend.example`; exact origin must match Gateway CORS and the return templates. |
| Stage/Prod Payment | `PAYMENT_CHECKOUT_SUCCESS_URL` and `PAYMENT_CHECKOUT_CANCEL_URL` both use `https://your-frontend.example/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}`; `PAYMENT_FRONTEND_RETURN_URL=https://your-frontend.example/payment/return`. |
| Stage/Prod Payment | Keep Razorpay and Sandbox disabled. Complete real Stripe staging checkout, signed-webhook/replay, expiry, and refund verification before setting backend `STRIPE_STAGING_VERIFIED=true`. No provider or Order lookup secrets belong in the frontend. |
| Stage/Prod Gateway | Public product/Auth routes, payment webhooks, CORS preflight including `Idempotency-Key`, rate-limit behavior, and protected-route access must pass stage smoke tests. |
| Stage/Prod Gateway | Checkout (`POST /api/v1/orders`) and cancellation (`PUT /api/v1/orders/*/cancel`) use the configured IP-based limits: 2 requests/second with burst 5 by default. Tune only through `GATEWAY_ORDER_CHECKOUT_RATE_LIMIT_*` and `GATEWAY_ORDER_CANCEL_RATE_LIMIT_*`. |
| Stage/Prod Cart | `CART_GUEST_COOKIE_SECURE=true`; set `CART_GUEST_COOKIE_SAME_SITE` intentionally for deployment topology. |
| Stage/Prod Auth | Secure HttpOnly refresh cookie and compatible SameSite policy; align `VITE_SESSION_IDLE_TIMEOUT_MS` / `VITE_SESSION_WARNING_MS` with Auth browser-session timeouts. |

Use the [Payment production reliability runbook](../payment-service/docs/production-reliability.md)
for the coordinated migrations, secrets, Kafka permissions, and staging verification.
