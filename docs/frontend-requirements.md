# E-commerce Frontend Requirements

## 1. Purpose

Build a responsive web application for the existing e-commerce platform. The
application lets customers discover products, maintain a guest or signed-in
cart, check out, pay, and review orders; sellers manage their own catalogue and
stock; and administrators perform the limited, documented operational tasks.
The frontend communicates exclusively with the API Gateway; it does not call a
microservice port directly.

## 2. Product scope

### Stage release (customer storefront)

| Area | Required capability |
| --- | --- |
| Catalogue | Browse active products, search, filter, sort, paginate, and view product details. |
| Identity | Register, verify email, log in, refresh a session, log out, recover access, and manage profile security. |
| Guest cart | Add, change quantity, remove and retain cart items before login. |
| Customer cart | Merge a guest cart at login and manage the signed-in cart. |
| Checkout | Collect a shipping address, validate it, create an idempotent order. |
| Payment | Show preparation state, start provider checkout, show final order/payment state. |
| Orders | List the customer's orders, view order details, cancel an eligible order. |

### Stage release (role-protected workspaces)

| Area | Required capability | Boundary |
| --- | --- | --- |
| Seller catalogue | List, single/bulk create, fully edit, deactivate/reactivate, and archive owned products. | No media upload, variants, SKU, bulk edit, or server-side workspace search/filter API. |
| Seller stock | Read/create/update inventory for a known owned product. | Updates replace the absolute available quantity; there is no inventory list or history. |
| Seller orders | View the seller-scoped paginated order queue. | Read-only; no seller order detail, fulfilment, shipping, refund, payout, or customer-message API exists. |
| Admin users | List/read users, change status or complete role set, revoke sessions, and soft-delete. | Permissions remain backend-enforced; no search/filter API exists. |
| Admin catalogue | Create for an eligible seller; read/edit/reactivate/archive by known ID; replay/reconcile Product delivery. | There is no administrative all-products list/search endpoint; bulk import is seller-only. |
| Admin stock | Create/read/update inventory by known product ID. | No admin inventory list/search or adjustment history exists. |
| Admin orders | View the paginated queue and make documented status transitions. | No deep-link detail API or fulfilment/tracking flow exists. |
| Admin payments | List/read payments and request a documented refund. | Never expose raw provider diagnostics. |
| Admin notifications | Read failed-notification diagnostics only where the operator contract is enabled. | No customer inbox/preferences; diagnostic DTOs are not yet a stable public UI contract. |

### Explicitly deferred

- Customer notification inbox and notification-preferences centre. These are a
  later release after the Notification Service exposes versioned, paginated,
  privacy-safe DTOs and enforced preference categories.
- Product reviews, wishlists, promotions, fulfilment tracking, push notifications,
  and marketing features.

These can be planned as later releases after the customer checkout path is stable.

## 3. Users and permissions

| User | Goal | Access |
| --- | --- | --- |
| Guest | Discover products and build a cart without an account. | Public catalogue and `/api/v1/cart/guest/**`. |
| Customer | Purchase and manage their account/orders. | Authenticated customer APIs. |
| Seller | Manage owned products/stock and view seller-relevant orders. | `/seller/**`; `SELLER` or `ADMIN`, except bulk import requires SELLER. Product list/create use JWT identity; authorized admin product support can manage another seller's known ID. |
| Administrator | Operate documented users, catalogue, orders, inventory, payments and diagnostics. | `/admin/**`; `ADMIN` role plus endpoint-specific permissions. |

The UI must use the roles in the access token/user profile to show only relevant
navigation. Role-based visibility is for usability; authorization remains the
backend's responsibility.

## 4. Customer journeys

### 4.1 Browse and add to cart

1. Visitor lands on the catalogue page and sees active products.
2. They search, filter by category, brand, and price, choose a sort order, open
   a product, choose quantity, and add it. Product price and availability are
   revalidated later by checkout.
3. Before authentication, the application uses the guest-cart API and includes
   browser credentials so the HttpOnly guest cookie is persisted. The browser
   never reads, stores, or sends a raw guest ID.
4. Cart item presentation is enriched from Product API data; Cart API data alone
   has only product IDs and quantities.

### 4.2 Guest cart merge

1. After a successful login, the UI starts one idempotent guest-cart merge using
   the browser's HttpOnly guest cookie and an `Idempotency-Key`.
2. It shows a merge-in-progress state and disables duplicate merge requests.
3. A successful merge returns the authoritative customer cart; the guest cart is
   retired by the platform.
4. A retryable conflict/lock/dependency failure preserves the visible cart and
   offers a bounded retry. A completed merge may safely be retried with the same
   idempotency key without increasing quantities again.

### 4.3 Register and sign in

1. A user submits name, email, and a password of at least 12 characters. Public
   registration creates a `CUSTOMER` account only.
2. The UI confirms that verification email was requested without exposing account
   details or claiming that an email was delivered.
3. The email link opens `/verify-email?token=...`. The page reads the token and
   automatically posts `{ "token": "..." }` to
   `POST /api/v1/auth/verification/confirm`; users never type or copy a token.
4. The page presents three outcomes: verifying, verified (login action), or
   invalid/expired/previously-used link (resend verification action). The API
   deliberately returns the same `401` response for those final cases, so the UI
   must not claim that it can identify an "already verified" account.
5. An active verified user logs in; the UI keeps session state in memory and fetches their
   profile.
6. Immediately after successful login, it calls `POST /api/v1/cart/merge-guest`.

### 4.4 Password recovery

1. A customer submits their email address from Forgot password.
2. The UI always confirms that an email will be sent when eligible; it never
   reveals whether an account exists or is active, or whether delivery succeeded.
3. The email opens `/reset-password?token=...`; the customer enters a new
   password meeting the shared 12-character minimum.
4. The page automatically posts the token and new password. On success it
   explains that all sessions were signed out and offers Sign in.
5. An invalid, expired, or previously-used link offers a new password-reset
   request. The UI does not distinguish those causes.

### 4.5 Email change and account security

1. A signed-in customer requests a different email address from Account.
2. The backend requests delivery of an email containing
   `/confirm-email-change?token=...`; the UI asks the customer to check the inbox
   without claiming delivery succeeded.
3. The confirmation page automatically posts its one-time token to the public
   email-change confirmation endpoint. It must work on any device and must not
   require an existing browser session.
4. On success, the backend changes the email, revokes all sessions, and the UI
   clears local session state then routes to Sign in.
5. Account also provides active-session listing/revocation, password change,
   and a destructive delete-account confirmation. Security-changing actions can
  end the current session.

### 4.6 Checkout and payment

1. Signed-in customer opens cart and proceeds to checkout.
2. They enter a complete shipping-address snapshot and submit it with cart items.
   There is no saved-address or Address Service flow in release one.
3. Order Service revalidates product eligibility, current price, requested
   quantity, and Inventory reservation before creating the checkout outcome.
4. The UI generates a checkout UUID and sends it as the required
   `Idempotency-Key` to `POST /api/v1/orders`. Network retries use the exact same
   key and unchanged body; a cart or address change creates a new intent/key.
5. The UI shows an order-created/pending-payment state and polls the order and
   payment record using bounded retry/backoff. A temporary payment `404` or
   `PAYMENT_PREPARING` means payment preparation is still in progress.
6. Once available, the UI requests the checkout session and redirects/opens the
   provider's supplied checkout flow.
7. On return, the UI polls the authoritative order/payment status and presents
   the verified result. A provider return URL by itself is never proof of payment.
8. A pending payment may expire. The UI shows an expired state, refreshes the
   cart, and starts a new checkout intent rather than attempting to reuse the
   expired order.

### 4.7 Order management

1. Customer opens order history, filters by status, and views a single order.
2. For an eligible order, the customer may request cancellation.
3. The UI refreshes the order until the backend reports the resulting state.

### 4.8 Seller workspace

1. An administrator assigns the `SELLER` role; public registration always creates
   a customer and there is no seller self-onboarding flow.
2. A seller signs in and sees only their paginated products. Create and edit use
   the full product representation, so an edit form preserves every optional
   field rather than accidentally clearing it.
3. Seller inventory is loaded by product ID. A product lifecycle snapshot may provision
   inventory asynchronously, so a temporary `404` after creation is shown as
   “inventory is being prepared” with a bounded manual retry.
4. Stock input means **replace available stock with this number**; it is never a
   delta/adjustment control. The UI does not make a low-stock or reservation claim.
5. The seller order queue is read-only. It contains only seller-owned line items;
   address data is shown only in that fulfilment-context screen and is never logged.

### 4.9 Administrator workspace

1. An administrator signs in and sees role navigation. The backend remains the
   authorization authority; missing endpoint permissions result in access denied.
2. User role editing always submits the full desired role array and asks for a
   confirmation, especially when removing `ADMIN`; status/role changes and
   deletion revoke the affected user's sessions.
3. Catalogue and inventory management require a known product ID because the
   current API has no administrative listing/search contract. The UI must make
   that limitation explicit instead of pretending to query all products.
4. Admin order status controls present only backend-valid transitions. The current
   workflow supports `PENDING → CONFIRMED` or `CANCELLED`, and `CONFIRMED →
   CANCELLED`; it is not a fulfilment console.
5. Payment pages use safe status/amount/order data and a confirmed refund request;
   they never render raw provider identifiers or failure payloads. Notification
   diagnostics are operator-only, read-only, and must not expose raw payloads.

## 5. Information architecture and page inventory

| Route | Page | Primary contents |
| --- | --- | --- |
| `/` | Catalogue | Header, search, category/brand/price filters, sort controls, product-card grid, pagination. |
| `/products/:productId` | Product detail | Gallery, description, price, category/brand, quantity control, add-to-cart. |
| `/cart` | Cart | Enriched line items, quantity controls, totals shown as estimates, checkout CTA. |
| `/login` | Login | Email/password form, reset-password link. |
| `/register` | Registration | Name/email/password form and verification guidance. |
| `/verify-email?token=...` | Email verification | Automatic link-token confirmation; verifying, verified, or a generic invalid/expired/used-link state with resend. |
| `/forgot-password`, `/reset-password?token=...` | Password recovery | Generic request-success state; automatic token-based reset confirmation; invalid/expired/used-link recovery. |
| `/confirm-email-change?token=...` | Email change confirmation | Automatic public token confirmation; success clears all sessions and routes to sign in. |
| `/checkout` | Checkout | Address form, order summary, submit action. |
| `/payment/return?orderId=&paymentId=` | Payment return | Pending verification, confirmed, failed, cancelled, expired, and retry guidance. |
| `/orders` | Order history | Paginated customer order list and status filters. |
| `/orders/:orderId` | Order detail | Items, address snapshot, price/total, payment and cancel status. |
| `/account` | Account | Profile update, password, email-change and active-session actions. |
| `/seller` | Seller overview | Capability summary and explicit backend-contract boundaries; no fabricated metrics. |
| `/seller/products` | Seller products | Progressive owned-product list, single/bulk create links, edit/visibility/archive actions. |
| `/seller/products/import` | Seller bulk import | JSON preview and validation for 1..100 owned products; SELLER role required. |
| `/seller/products/new`, `/seller/products/:productId/edit` | Seller product editor | Full product form, URL-only images, validation, destructive-action confirmation. |
| `/seller/inventory`, `/seller/products/:productId/inventory` | Seller inventory | Read/create/replace stock for a known owned product; temporary provisioning retry state. |
| `/seller/orders` | Seller order queue | Read-only seller-scoped pagination and fulfilment-context address snapshot. |
| `/admin` | Admin overview | Safe operational navigation and known-contract limitations. |
| `/admin/users`, `/admin/users/:userId` | Admin users | Paginated users plus status, full-role replacement, session revocation, and soft-delete confirmations. |
| `/admin/catalogue`, `/admin/catalogue/new`, `/admin/catalogue/:productId/edit` | Admin catalogue | Managed read/create/edit/visibility/archive by known ID; confirmed Product replay/reconciliation; explicit no-list/search state. |
| `/admin/inventory` | Admin inventory | Read/create/replace stock by known product ID. |
| `/admin/orders` | Admin orders | Paginated queue and allowed status transitions only. |
| `/admin/payments` | Admin payments | Paginated list with an in-page safe payment detail and confirmed refund request. |
| `/admin/notifications` | Admin notifications | Redacted failed-notification diagnostics and confirmed Auth outbox replay; no raw payload display or customer inbox. |

## 6. Functional requirements

### Catalogue

- This section follows the implemented [Product API contract](../product-service/docs/api.md).
- Use `GET /api/v1/products` with `q`, `category`, `brand`, `minPrice`,
  `maxPrice`, `sort`, `page`, and `size` query parameters.
- Support independent and combined filters, including one-sided price ranges.
  Validate `minPrice <= maxPrice` before the request; backend also rejects an
  invalid range, unsupported sort, negative page, or out-of-range page size.
- Use the Product facets contract to populate category and brand filters rather
  than hardcoding catalogue values.
- Keep search, filters, sort, page, and page size in the URL so results are
  shareable and back/forward navigation works.
- Product cards show image fallback, name, price with ISO currency, brand/category,
  and an accessible add-to-cart action.
- Public list and product-detail endpoints expose active products only. Treat a
  `404` detail response as product-not-found; do not create a public inactive
  product page.
- Do not show stock quantities, "in stock" claims, delivery promises, ratings,
  reviews, variants, SKU, or promotion pricing until a separately contracted API
  provides them. Checkout remains authoritative for final eligibility, price,
  and stock.

### Product API integration assumptions

- `GET /api/v1/products/facets` returns global active category/brand counts,
  grouped case-insensitively; current search filters do not narrow these facets.
- Product lifecycle events are published reliably for product creation, update,
  deactivation, reactivation, and archival.
- Product responses use UTC-aware timestamps and include currency with price.
- Product images use approved HTTPS CDN/storage URLs; the UI still provides an
  image fallback for unavailable assets.

### Cart

- This section assumes the Cart Service production-hardening ticket is complete.
- Every guest-cart request uses `credentials: "include"`.
- Guest routes begin with `/api/v1/cart/guest`; signed-in routes use `/api/v1/cart`.
- Never expose a guest-cart UUID to browser JavaScript or use the raw `guestId`
  merge-body fallback. Browser merge relies on the HttpOnly cookie only.
- Send an `Idempotency-Key` for add-item, merge, and other retryable mutations.
  Reuse the original key only for the same intended operation.
- Send the current cart version or concurrency token for customer mutations when
  the completed API contract requires it. On a conflict, reload the authoritative
  cart, explain that it changed elsewhere, and let the customer retry deliberately.
- Respect API-provided cart line/quantity limits and prevent invalid values before
  submission. Quantity updates replace the value; add-item increments the existing
  product line.
- Refresh displayed cart state from each successful mutation response. A `404`
  item/cart mutation may mean expiry or concurrent removal: reload cart and show
  an appropriate recovery message.
- Cart responses contain composition only (`productId`, `quantity`, item identity,
  owner type/ID, version, and UTC `updatedAt`). Enrich display data through Product
  Service; never treat cart prices, totals, or stock as authoritative.
- Checkout revalidates products, quantities, prices, and stock. Recognize checkout
  revalidation failures and return the customer to a refreshed cart. Inventory
  reservation, release, expiry, and deduction are internal Order/Inventory work;
  the browser never calls Inventory Service.
- Do not clear a cart directly after order creation from the browser. Apply the
  platform's defined post-order cart result after the authoritative order/payment
  outcome is known.

### Seller workspace

- Guard `/seller/**` with `SELLER` or `ADMIN` navigation. The Gateway and each
  service remain the final authorization authority. Product list/create use the
  signed-in identity; authorized admin managed-product reads/mutations can support
  another seller's known product ID. Bulk import requires SELLER.
- Use `GET /api/v1/seller/products?page=&size=` for the owned list and managed
  `GET /api/v1/seller/products/{id}` for editors, including inactive records after
  reload. Search loaded products is a client filter; no seller server-side search,
  filter, sort, bulk-edit, SKU/variant, or media-upload endpoint exists.
- Bulk import uses `POST /api/v1/seller/products/bulk` with 1..100 validated
  products. Only SELLER can import; the browser cannot choose another owner.
- Create/update product fields are `name`, positive `price`, ISO `currency`, optional
  `description`, `category`, `brand`, and at most ten HTTPS `imageUrls`. Updates
  are full replacements; preserve optional values in the edit form. Only update
  accepts `active`; create always starts active. DELETE archives and preserves
  history; enabling visibility and saving reactivates an eligible seller's product.
- Seller inventory supports `POST /api/v1/seller/inventory` and known-product
  `GET`/`PUT`. A write sets absolute `availableStock`, never a delta, and the UI
  exposes read-only `reservedStock` only in this privileged operational screen.
  A 404 shortly after product creation can be asynchronous provisioning; offer a
  bounded retry/create-recovery state.
- Seller orders use only `GET /api/v1/seller/orders?page=&size=`. Show the
  read-only seller-scoped queue, never fabricate detail, shipment, cancellation,
  refund, payout, or messaging controls. Treat shipping addresses as sensitive
  fulfilment data and never log or expose them outside the queue's need-to-know
  context.

### Administrator workspace

- Guard `/admin/**` with `ADMIN`. Endpoint permission claims, if present in the
  session profile, may disable unavailable controls for usability; absence of
  claims must not be mistaken for permission, and every action relies on the
  backend result.
- Use the documented paginated `/api/v1/admin/users` list and known-user detail.
  Changing roles replaces the complete role set; require confirmation and prevent
  self-demotion, self-session-revocation, or self-deletion in the UI. Role,
  status, and deletion actions can revoke the affected user's sessions.
- Admin product create/read/edit/archive uses known IDs; managed
  `GET /api/v1/admin/products/{id}` includes inactive products. There is no admin
  product directory. Use the managed detail response to prefill edits.
- Product recovery has confirmed replay and reconciliation actions at
  `/api/v1/admin/products/outbox/**`. Reconciliation queues up to 100 products per
  request and offers the returned cursor's next batch until complete.
- Admin inventory is also known-product-ID-only. `POST` creates a row and `PUT`
  replaces `availableStock`; no list, safe stock delta, reservation reconciliation,
  or history is available.
- Use the admin order list and render only server-valid transitions: `PENDING`
  to `CONFIRMED`/`CANCELLED`, and `CONFIRMED` to `CANCELLED`. There is no admin
  order-detail, fulfilment, tracking, or general status-management contract.
- List/read admin payments and submit a confirmed refund with a stable
  idempotency key. A refund request is not proof of completion. Restrict provider
  fields to minimal safe display and never show raw provider failure payloads.
- Failed-notification diagnostics remain read-only and operator-only. Redact
  recipient/message/payload data. The separate confirmed Auth outbox replay action
  requeues terminal Auth deliveries; Product replay/reconciliation lives in admin
  catalogue operations. Neither exposes raw event payloads or token values.

### Session and account

- Keep access tokens in memory and send them as `Authorization: Bearer <accessToken>` to authenticated Gateway requests. The refresh secret is an HTTP-only, Secure production cookie and must never be stored in browser web storage.
- On authenticated `401`, join a single-flight cookie-backed refresh and retry
  once. If refresh fails, clear local authentication and require sign-in,
  preserving only a safe intended destination where appropriate.
- On `403`, show an access-denied screen instead of retrying.
- Support token refresh before expiry or after an authentication failure, with a
  single-flight refresh mechanism so concurrent failed calls do not create races.
- Restore through the cookie-backed refresh endpoint on startup; protected routes
  wait for completion. Recent activity permits silent renewal. The local inactivity
  countdown defaults to 30 minutes with a five-minute Continue session / Sign out
  warning. Align its settings with Auth; server idle/absolute expiry remains
  authoritative. Refresh/logout requests never send a refresh-token JSON body.
- Never log tokens, passwords, reset tokens, or personally identifiable address data.

### Auth flows and recovery

- Treat every verification, password-reset, and email-change link token as
  one-time and expiring after 30 minutes. A new verification resend invalidates
  the older verification link.
- Verification resend and password-reset request always show the same success
  message after `202`, whether the submitted email is unknown, already active,
  or eligible. Never use the response to expose account existence or status.
- Login returns the same `401 Invalid credentials` outcome for an unknown email,
  incorrect password, unverified account, suspended account, or deleted account.
  Offer a separate verification-resend route, but do not infer account status.
- Use the shared 12-character password minimum in registration, reset, and
  password-change forms; registration now enforces the same backend minimum.
- On a refresh failure or detected refresh-token reuse, clear the session and
  take the user to sign-in. Refresh requests must be single-flight so parallel
  `401`s cannot rotate the token more than once.
- Email change is a two-step flow: request the new email while signed in, then
  open `/confirm-email-change?token=...`. The confirmation page posts the token
  to the public email-change confirmation endpoint and must work without an
  existing session. On success, all refresh sessions are revoked, so clear local
  tokens and send the user to sign-in.
- Password reset, password change, successful email change, account deletion,
  and security/session actions can revoke sessions. Preserve only a safe return
  path; never preserve credentials or tokens in URLs/local logs.
- A `404` while revoking a session can mean the session was already revoked or
  is not owned by the current user. Refresh the session list and show a neutral
  "session is no longer active" message.

### Notification delivery expectations

- Release one has no notification inbox, delivery-attempt view, or notification
  preference screen. Email is an asynchronous backend concern.
- After registration, verification resend, password reset, or email-change request,
  show a neutral request-accepted message such as “Check your inbox if eligible.”
  Do not claim delivery, expose an email-provider failure, or disclose account
  existence.
- Auth email-link screens retain their existing resend/recovery actions. A missing
  email is handled through a safe resend request, not a browser-side delivery
  status check.
- When a later Notification Centre release is approved, use only the versioned,
  paginated, frontend-safe Notification APIs. Render safe type/status/summary and
  timestamps; never render raw payloads, action IDs, action URLs, email addresses,
  provider message IDs, retry counts, or provider error text.
- A future Preferences screen must render server-provided types/categories only,
  keep required security/transactional notices non-disableable, and show an
  explanatory disabled state rather than attempting to override backend policy.

### Checkout and payment

- This section assumes the Inventory, Order, and Payment Service hardening tickets are complete.
- Require a signed-in customer before checkout.
- Validate required shipping fields before submitting, while displaying backend
  validation errors by field.
- The order body contains `productId`, `quantity`, a complete `shippingAddress`,
  and optional `currency`; never send client-calculated price, total, reservation
  ID, or `shippingAddressId`.
- `Idempotency-Key` is required. Disable duplicate submit while a checkout request
  is pending. A retry after timeout/network loss must reuse its original key and
  byte-equivalent intended request; a changed cart/address starts a new checkout
  intent and UUID. `409 IDEMPOTENCY_KEY_REUSED` means the user must deliberately
  review and resubmit rather than blindly retry.
- Recognize `CHECKOUT_ITEM_*` failures: reload catalogue/cart display and ask the
  user to review the affected item before retrying. Use structured `code`,
  `retryable`, and safe `details`; never parse text error messages.
- Treat insufficient stock, inventory reservation expiry, product deactivation,
  and temporary inventory dependency failure as Order/checkout outcomes. Show a
  clear recovery state and a refreshed cart; never display raw inventory counters
  or instruct the customer to retry internal inventory operations.
- Treat `PAYMENT_EXPIRED` as terminal for that checkout intent: refresh cart/order
  state and create a new checkout intent. Treat cancellation/refund eligibility as
  an Order Service decision; only render Cancel when `cancelAllowed` is true.
- After order creation, poll `GET /api/v1/payments/orders/{orderId}` with bounded
  exponential backoff. A temporary `404` or `PAYMENT_PREPARING` is expected while
  the durable Order-created event is being processed. Stop polling after a bounded
  window and provide a safe retry/refresh action.
- Request `POST /api/v1/payments/orders/{orderId}/checkout-session` only after the
  payment record is ready. The response supplies `paymentId`, `checkoutUrl`,
  provider, status, and UTC `expiresAt`; redirect only to the approved URL returned
  by the API. Never construct a provider URL in the browser.
- Provider redirect returns to `/payment/return?orderId=&paymentId=`. Verify the
  URL identifiers are well-formed, then use authenticated Order and Payment APIs
  to determine the outcome. The redirect, query parameters, or provider page text
  are never payment proof.
- For `PAYMENT_PROVIDER_UNAVAILABLE` or other retryable payment-session errors,
  preserve the order context and offer bounded retry. For non-retryable terminal
  states, show the authoritative order state and do not retry provider creation.
- Do not render raw provider failure reasons, provider transaction IDs, payment
  attempt IDs, webhook data, or provider implementation details.
- Render order history from immutable Order snapshots (`productName`, quantity,
  unit price, line total, address snapshot, total, currency, and status). Do not
  fetch current Product Service data to make a past order readable.
- Do not promise availability, stock count, or delivery timing before the
  authoritative checkout/order result.
- Poll briefly with bounded retry/backoff; do not poll indefinitely in the
  background.

## 7. Error, loading, and empty states

Every page must define loading, empty, error and success states.

| Condition | UI behavior |
| --- | --- |
| `400` validation map | Display field-level messages and retain entered values. |
| API error body / Problem Details | Display a safe user message; do not parse error-message text for logic. |
| Authenticated `401` | Attempt one shared refresh and one retry; failed refresh clears session and requires sign-in. |
| `403` | Explain that the account lacks access. |
| `409` cart version/conflict | Reload the authoritative cart, explain that it changed in another tab/session, and let the customer retry deliberately. |
| `409 IDEMPOTENCY_KEY_REUSED` | Do not submit again automatically. Explain that the checkout intent differs from the earlier submission; refresh the cart/order view and let the customer start a deliberate new checkout intent. |
| `429` | Explain that too many attempts were made; disable repeat submission until the supplied retry time, if present. |
| Gateway `429` | Respect `Retry-After`, preserve safe input, and do not retry automatically before the limit window ends. |
| `404` | Use a product/order not-found state; payment polling may retry a new order's temporary 404. |
| Product detail `404` | Show product-not-found content and offer a return to catalogue. |
| `503` | Show retry action and preserve current user input. |
| Gateway `503` / `504` | Explain that the service is temporarily unavailable, preserve safe input, and offer bounded retry without naming internal services, Redis, or circuit breakers. |
| Cart lock/Redis retryable failure | Preserve the rendered cart and entered quantity, use bounded retry/backoff, and avoid repeating an add/merge with a new idempotency key. |
| Guest cookie unavailable | Explain that the guest cart cannot be retained in this browser/domain configuration; do not substitute an exposed browser guest ID. |
| Checkout insufficient stock/quantity limit | Refresh the cart, identify the affected item where the Order API safely provides it, and ask the customer to reduce/remove it before retrying. |
| Checkout inventory dependency failure | Preserve checkout/cart input, explain that checkout is temporarily unavailable, and offer bounded retry without exposing internal inventory details. |
| Checkout reservation expired/cancelled | Refresh order/payment state and cart; ask the customer to start a new checkout intent if the order cannot continue. |
| `PAYMENT_EXPIRED` | Explain that payment was not completed in time, refresh the cart, and create a new checkout intent. Never reuse the expired order or its idempotency key. |
| `PAYMENT_PREPARING` / temporary payment `404` | Keep the order context, poll with bounded exponential backoff, then provide refresh/retry guidance. Do not create a second order. |
| `PAYMENT_PROVIDER_UNAVAILABLE` | Preserve order context, explain that payment is temporarily unavailable, and offer bounded retry of checkout-session creation. |
| Payment failed/cancelled | Explain that the order was not confirmed, refresh authoritative order/payment state, and provide the documented new-attempt or cart recovery action. |
| Payment provider return | Show a verifying-payment state until authenticated Order/Payment API responses determine the outcome. Never use return URL/query text as success evidence. |
| Order cancellation/refund unavailable | Show the backend-provided safe reason and support/contact path where applicable. Do not imply that a cancellation or refund occurred until the authoritative order state changes. |
| Invalid, expired or used verification/reset link | Explain that the link can no longer be used; offer resend verification or password-reset request. Do not identify the account as already verified. |
| Invalid, expired or used email-change link | Explain that confirmation cannot continue; preserve no token and direct the user to sign in and start a new email-change request. |
| Expected email has not arrived | Offer the relevant bounded resend/request action after any documented cooldown. State that delivery can be delayed; do not show provider diagnostics or whether an account exists. |
| Empty catalogue/cart/orders | Explain the state and offer a relevant next action. |
| Network offline | Show connectivity status and allow the user to retry. |

## 8. UX and visual requirements for Figma

### Design direction

- Trustworthy, modern marketplace; light default theme; mobile-first responsive
  layouts for 360px, 768px and 1440px widths.
- Prioritize clear product imagery, readable price hierarchy, compact navigation,
  and a deliberately calm checkout.
- Use an 8px spacing scale, accessible color contrast (WCAG AA), visible keyboard
  focus, and semantic labels for forms and controls.
- Use one reusable component set: button, icon button, input, select, quantity
  stepper, product card, cart line, badge, alert, modal, pagination, skeleton,
  empty state, toast and order-status chip.

### Required Figma pages

1. `00 - Cover & flows`: product purpose, customer-flow, seller-flow, and operator-boundary diagrams.
2. `01 - Foundations`: color, typography, spacing, radius, shadow, icon rules.
3. `02 - Components`: reusable variants and states.
4. `03 - Customer desktop`: catalogue, product detail, cart, login, checkout,
   payment return, orders, order detail, account.
5. `04 - Customer mobile`: the same core journey at 360px width.
6. `05 - Seller & admin`: seller catalogue/stock/order-queue and admin user/catalogue/inventory/order/payment/diagnostic screens, including every known-ID and contract-pending boundary.
7. `06 - Prototype`: linked browse → cart → login → checkout → payment-return flow plus role-aware workspace entry.

The design must include default, hover, focus, disabled, loading, validation-error,
empty and unavailable states—not just happy-path screens.

### Required catalogue frames and states

- Catalogue default, first-load skeleton, paginated loading, empty results,
  offline/dependency failure, and public product-not-found states.
- Search input, category/brand facets, minimum/maximum price inputs, active-filter
  chips, clear-all action, supported sort options, and URL-preserved filter state.
- Product card image fallback, price with currency, add-to-cart loading/success,
  and checkout-revalidation guidance. Do not include uncontracted stock, ratings,
  review, variant, SKU, promotion, or delivery claims.
- Product detail gallery/image fallback, description, category/brand, quantity
  control, add-to-cart actions, and a product-not-found screen for a `404`.

### Required cart frames and states

- Guest cart and signed-in cart, enriched product-line presentation, quantity
  replacement controls, remove/clear confirmation, empty-cart, and image fallback.
- Add-item, quantity-update, remove, and clear loading states. Include a cart
  changed-elsewhere conflict state that reloads the authoritative cart.
- Guest-to-customer merge in-progress, merge complete, retryable merge failure,
  and expired/evicted-cart recovery states.
- Checkout revalidation state for removed, inactive, unavailable, repriced, or
  quantity-limited cart items. Cart never presents its own totals or stock as final.
- Post-order cart state must reflect the platform's defined retained/cleared/
  selectively-updated behavior after the authoritative order/payment result.

### Required checkout inventory states

- Checkout processing state that explains final price and availability are being
  confirmed without exposing internal Inventory Service details.
- Insufficient-stock/quantity-limited item recovery, deactivated product recovery,
  temporary inventory dependency failure with bounded retry, and expired/cancelled
  reservation recovery that starts a new checkout intent when required.
- Payment pending/success/failure/cancelled screens must use Order/Payment status
  as the customer-facing outcome; never display reservation IDs or stock counters.

### Required order and payment states

- Checkout submit/loading, duplicate-submit prevention, order-created and
  payment-preparing states, including a bounded temporary-payment-404 retry state.
- Checkout-session request/loading, retryable provider-unavailable, and safe
  non-retryable terminal-state screens. The provider redirect action is disabled
  while a session request is pending.
- Payment provider return: pending verification, confirmed, payment failed,
  cancelled, payment-expired, refund-processing, refunded, refund-failed, and
  fulfilment-review informational states. A provider redirect alone must never be
  designed as a success confirmation.
- Payment status polling with a short bounded backoff timeline, stop/retry state,
  and accessible non-technical messaging. Never display provider transaction,
  attempt, webhook, or reservation IDs.
- Order history: loading, empty, status-filtered, pagination, dependency failure,
  and order-not-found states.
- Order detail: immutable item name/quantity/unit-price/line-total snapshots,
  total/currency, shipping-address snapshot, status history/outcome messaging,
  and cancel action only where `cancelAllowed` is true.
- Cancellation requested/processing, cancellation complete, cancellation no longer
  available, refund/fulfilment-review informational states, and safe retry/error
  states. Never expose payment, inventory, reservation, or internal event IDs.

### Required Auth frames and states

- Registration form, inline validation, submitting state, duplicate-account
  recovery, and check-email confirmation.
- Verify-email: token-processing, verified/sign-in, and generic
  invalid/expired/used-link with resend form and rate-limit state.
- Login: default, field validation, generic invalid-credentials, loading,
  session-expired/sign-in-again, and rate-limited states.
- Forgot password: form, generic request-success, validation, offline/server
  failure, and rate-limited state.
- Reset password: token-processing, new-password validation, success/sign-in,
  and generic invalid/expired/used-link recovery.
- Account: profile name update, request email change, check-new-email state,
  automatic email-change processing, email-changed/sign-in-again, password
  change, active sessions/revoke state, and delete-account confirmation.
- Every email-link screen must state that tokens are never entered manually,
  are single-use, and may expire.
- Do not add a customer notification inbox, delivery-history, provider-status, or
  preference frames. The stage admin diagnostic screen may show only redacted,
  read-only failed-notification metadata. If a customer Notification Centre is
  approved later, design safe paginated history, loading/empty/error states, and
  server-controlled preference categories with required notices visibly locked.

## 9. Technical constraints

- Frontend base URL: `VITE_API_BASE_URL=http://localhost:8080` for local work.
- Frontend build/runtime configuration may contain only explicit public values, for
  example `VITE_API_BASE_URL`, application name, public branding, and approved
  public feature flags. It must never contain Config Server URLs, service ports,
  database/Kafka/Redis details, vault references, internal tokens, OAuth secrets,
  JWT keys, or payment/email provider credentials.
- Call the API Gateway only. Service ports such as 8081/8082/8085 are not browser
  API origins.
- Local Gateway CORS defaults include `http://localhost:3000` and
  `http://localhost:4200`; configure the exact hosted frontend origin in
  `GATEWAY_CORS_ALLOWED_ORIGINS` outside development.
- The deployed Gateway must allow the browser request headers `Authorization`,
  `Content-Type`, `Idempotency-Key`, `traceparent`, and `baggage`. `Idempotency-Key`
  is required for checkout and contracted retryable mutations, so a failed CORS
  preflight is an environment defect, not a checkout business error.
- Use TypeScript and generate/maintain API types from each Gateway-exposed OpenAPI
  endpoint where practical.
- Ensure guest-cookie `SameSite` and `Secure` configuration matches the production
  frontend/Gateway domain topology.
- Public browser flows depend on Gateway explicitly permitting public catalogue
  reads, guest-cart routes, public Auth link-token confirmations, and provider
  webhook paths. All other customer routes require normal authenticated Gateway
  access; seller/admin workspace routes require their documented role and service
  permission gates.

### Gateway integration and recovery

- Use Gateway as one canonical HTTPS origin per environment. Do not create
  frontend fallbacks to direct service URLs when a Gateway route is unavailable.
- Send `credentials: "include"` on Gateway requests for guest-cart and Auth cookies. Send bearer authorization
  only where required by the API-client session policy.
- On Gateway `429`, prevent repeat submission and respect `Retry-After` when
  supplied. Keep user input/cart state intact.
- On retryable Gateway `503` or `504`, preserve safe input and offer bounded retry.
  Do not name an internal service or expose circuit-breaker/Redis details.
- On Gateway `401`, apply the established token refresh/sign-in flow. On `403`,
  show access denied and do not retry automatically. On Gateway `404`, show a
  normal not-found state unless it occurs while the payment-preparation polling
  window is still active.
- Generate the frontend request/correlation ID only if the platform documents a
  supported header; otherwise read safely exposed trace IDs for support diagnostics
  and never display raw infrastructure identifiers in normal UI.

### Environment release checks

- Treat frontend environment values as immutable per deployed build/release. A
  stage or production change to API origin, public feature flag, payment return
  origin, or email-link origin uses the controlled configuration/deployment
  promotion process rather than an unreviewed live browser change.
- Before deploying the frontend, verify the exact public frontend origin matches:
  `GATEWAY_CORS_ALLOWED_ORIGINS`, payment-provider return URL configuration, and
  Auth/Notification verification, reset-password, and email-change link origins.
- If these origins do not match, stop the deployment; do not attempt a browser-side
  workaround or direct-service fallback.
- Frontend build logs, monitoring, error reports, source maps, and support tools
  must redact access tokens, refresh tokens, one-time link tokens, addresses, and
  any accidentally supplied configuration value that appears sensitive.

## 10. Acceptance criteria for release one

- A guest can search, filter, sort, paginate active products, view a product, and
  maintain a cart through a browser refresh.
- A guest can add, replace quantity, remove, and retain cart items through the
  HttpOnly guest cookie; after sign-in, an idempotent merge produces one customer cart.
- A customer can register, verify, log in, merge the guest cart, and see its items.
- A customer can complete checkout once per intent without duplicate order creation.
- A customer receives safe checkout recovery for unavailable, quantity-limited,
  deactivated, or temporarily unreservable products without seeing internal stock data.
- The application correctly represents pending, successful, cancelled, failed,
  expired, refund, and fulfilment-review payment/order states from authoritative
  backend responses, never provider redirect parameters alone.
- A customer can find and view only their own orders and cancel an eligible order.
- Core desktop and mobile flows are usable by keyboard and pass basic accessibility
  checks.
- The app displays safe recovery UI for validation, authorization, dependency and
  network errors.

## 11. Deployment checks and remaining dependencies

1. **Public catalogue at Gateway:** anonymous catalogue list/detail/facets are
   explicitly permitted and covered by security tests. Stage smoke tests must
   verify the deployed route configuration matches that contract.
2. **Browser sessions:** implemented access-in-memory and HttpOnly refresh-cookie
   model. Verify HTTPS, exact credentialed CORS origins, cookie SameSite topology,
   and matching browser/server idle settings before deployment.
3. **Product images:** define a reliable CDN/storage and image fallback policy.
4. **Payment provider handoff:** confirm the checkout-session response schema and
   whether the frontend redirects to a URL or invokes a provider SDK.
5. **Brand direction:** confirm name, logo, preferred typeface, colors and imagery
   before final visual design. Figma can begin with neutral tokens and replace them
   once branding is approved.
6. **Auth ticket dependency:** the Auth Service production-hardening ticket must
   be implemented before frontend integration: public token-only email-change
   confirmation, atomic action-token consumption, shared password policy, public
   registration restricted to customers, rate limiting, refresh-token reuse
   invalidation, reliable outbox delivery, and stable error contracts.
7. **Product deployment:** the implemented contract covers validated queries,
   inactive visibility, transactional lifecycle delivery, archival, seller
   eligibility, UTC/currency and content rules. Deploy transaction-capable Mongo,
   shared events and Inventory consumer/config, then run reconciliation and the
   documented acceptance checks before claiming the deployed stack is ready.
8. **Cart ticket dependency:** the Cart Service production-hardening ticket must
   be completed before frontend integration: request limits/validation, safe
   concurrent mutations, idempotent merge and mutation retry, browser-safe guest
   identity/CSRF policy, retryable Redis/lock errors, UTC/versioned response
   contract, and defined post-order cart behavior.
9. **Inventory ticket dependency:** the Inventory Service production-hardening
   ticket must be completed before checkout integration: required idempotent
   reservation IDs, expiry/release compensation, invariant-safe stock adjustment,
   gRPC service security, product lifecycle synchronization, reconciliation, and
   documented Order-facing checkout failure codes.
10. **Order ticket dependency:** the Order Service production-hardening ticket
    must be completed before production checkout integration: mandatory
    payload-bound idempotency, transactional `order-created` outbox, durable
    failed-checkout compensation, pending-payment expiry, cancellation/refund and
    fulfilment rules, full shipping snapshots without a fictitious address ID,
    immutable item response snapshots, stable errors, bounded pagination, and
    UTC-aware timestamps.
11. **Payment ticket dependency:** the Payment Service production-hardening ticket
    must be completed before production payment integration: transactional payment
    outcome outbox, concurrency-safe checkout sessions and attempt idempotency,
    automatic payment expiry, Order-to-Payment cancellation/refund orchestration,
    durable refund retry/reconciliation, trusted Order validation, frontend return
    routes, provider/webhook hardening, stable errors, bounded pagination, and
    UTC-aware timestamps. Razorpay remains disabled until its adapter is complete
    and staging-verified.
12. **Notification ticket dependency:** the Notification Service production-
    hardening ticket must be completed before a customer notification inbox or
    preference UI is added: multi-instance-safe delivery claims, Kafka retry/DLT
    and replay, recipient-lag recovery, admin requeue/audit, versioned paginated
    safe DTOs, enforced notification categories/preferences, auth-link privacy,
    provider resilience, retention, and UTC-aware timestamps. Auth request screens
    may continue to show only neutral “check your inbox if eligible” messaging.
13. **Gateway ticket dependency:** the Gateway Service production-hardening ticket
    must be completed before production frontend deployment: explicit public
    catalogue/Auth/webhook routing, `Idempotency-Key` CORS support, route-specific
    rate limits, consistent edge errors, protected Notification admin routes,
    versioned/audited external route configuration, production proxy/TLS controls,
    and stage route/CORS/security smoke tests.
14. **Config Server ticket dependency:** the Config Server production-hardening
    ticket must be completed before production deployment: private authenticated
    configuration access, secret-manager-only production secrets, protected and
    immutable configuration releases, CI schema/security validation, stage/prod
    fail-fast startup, controlled refresh policy, and verified frontend/Gateway/
    payment/Auth origin alignment. Frontend bundles receive public configuration
    only.

## 12. Source contracts

- `docs/frontend-integration.md`
- `auth-service/docs/api.md`
- `product-service/docs/api.md`
- `cart-service/docs/api.md`
- `order-service/docs/api.md`
- `payment-service/docs/api.md`
- `notification-service/docs/api.md`
- `gateway-service/docs/api.md`
- `config-server/docs/api.md`
