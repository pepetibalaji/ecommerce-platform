# Frontend integration contract

Use Gateway as the only browser API origin. Do not call individual service ports from the frontend.

## Environment

```env
VITE_API_BASE_URL=http://localhost:8080
VITE_SESSION_IDLE_TIMEOUT_MS=1800000
VITE_SESSION_WARNING_MS=300000
```

For local development the Gateway CORS default permits `http://localhost:3000` and `http://localhost:4200`. Hosted stage/production deployments must set `GATEWAY_CORS_ALLOWED_ORIGINS` to the exact frontend origin. Browser API calls include `credentials: "include"` for both the guest-cart and Auth refresh HttpOnly cookies. Gateway CORS must also allow `Idempotency-Key`, `Authorization`, `Content-Type`, `traceparent`, and `baggage`; checkout cannot work from a browser otherwise.

Only public frontend values belong in the frontend environment/build configuration. Never include Config Server URLs, backend service ports, vault paths, databases, Kafka/Redis settings, internal service tokens, OAuth/JWT secrets, or payment/email provider credentials. Before a stage/production deployment, verify that the exact frontend origin matches Gateway CORS, payment-provider return URLs, and Auth/Notification email-link origins.

## Customer path

1. Browse `GET /api/v1/products`; list responses exclude inactive products.
2. Use `/api/v1/cart/guest/**` before authentication. After login, call `POST /api/v1/cart/merge-guest` with browser credentials.
3. Send bearer token on authenticated requests.
4. For every checkout intent, create a UUID in the browser and send it as `Idempotency-Key` on `POST /api/v1/orders`. Reuse that key only when retrying the same action.
   Only send `productId` and `quantity` for each item. `price` is accepted temporarily for legacy clients but ignored. The successful order response contains the authoritative item prices and total.
   If checkout returns a `CHECKOUT_ITEM_*` error (unavailable product, quantity limit, or insufficient stock), refresh the cart's product display and ask the customer to review the identified item before retrying.
5. Payment preparation follows asynchronous `order-created`. After checkout, poll `GET /api/v1/payments/orders/{orderId}` briefly; `404` can mean the payment is not prepared yet. Once it exists, call `POST /api/v1/payments/orders/{orderId}/checkout-session`.
6. Provider browser return pages are informational. Poll payment/order status; verified webhooks are authoritative.

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
normalizes that wire field internally and must send an admin refund idempotency
key in the JSON body (and may send the standard header). Admin user status PATCH
returns no body, so refresh the known user record after a successful update.

## API client rules

* Generate TypeScript types from Gateway OpenAPI routes where practical (`/product/v3/api-docs`, `/cart/v3/api-docs`, and so on).
* Handle `400` field errors from `ApiErrorResponse.fieldErrors`, with compatibility for legacy validation maps and Problem Details until errors are unified platform-wide.
* On authenticated `401`, attempt one shared cookie-backed refresh and one retry; failed refresh clears the session and sign-in is required. On `403`, show access denied. On `429`, respect `Retry-After` and prevent repeated submission. On retryable Gateway `503`/`504`, preserve safe input and provide bounded retry without exposing internal-service details.
* Never derive price, stock, or authorization from cart data in the browser; Product/Order/Payment responses are authoritative.
* Checkout limits default to 100 units per product and 500 total units. Operations can override them with `order.checkout.max-quantity-per-product`, `order.checkout.max-total-quantity`, and `order.checkout.product-maximum-quantities.<product-uuid>`.

## Required deployment variables

| Environment | Required frontend-related setting |
| --- | --- |
| Dev | `VITE_API_BASE_URL=http://localhost:8080` |
| Stage/Prod Gateway | `GATEWAY_CORS_ALLOWED_ORIGINS=https://your-frontend.example` |
| Stage/Prod Gateway | Public product/Auth routes, payment webhooks, CORS preflight including `Idempotency-Key`, rate-limit behavior, and protected-route access must pass stage smoke tests. |
| Stage/Prod Cart | `CART_GUEST_COOKIE_SECURE=true`; set `CART_GUEST_COOKIE_SAME_SITE` intentionally for deployment topology. |
| Stage/Prod Auth | Secure HttpOnly refresh cookie and compatible SameSite policy; align `VITE_SESSION_IDLE_TIMEOUT_MS` / `VITE_SESSION_WARNING_MS` with Auth browser-session timeouts. |
