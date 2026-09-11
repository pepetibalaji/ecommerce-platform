# Pepekart frontend

React, TypeScript, and Vite frontend for the ecommerce platform.

## Scope

- Customer storefront: catalogue, guest/signed cart, auth, account security, checkout, payment verification, and orders.
- Seller workspace: owned product management, inventory, and seller-order views.
- Admin workspace: user, product, inventory, order, payment, and notification operations.

The seller/admin workspaces intentionally expose only documented backend actions.
They do not invent aggregate analytics, server-side workspace search, fulfilment, payouts,
or customer-notification functionality where no API exists.

All browser requests use the API Gateway through `VITE_API_BASE_URL`; never expose a downstream service URL or secret in this app.

Browser sessions keep the access token in memory. Auth issues and rotates the refresh secret in an HttpOnly cookie; frontend JavaScript never receives it. `VITE_SESSION_IDLE_TIMEOUT_MS` defaults to 30 minutes and `VITE_SESSION_WARNING_MS` defaults to five minutes; align these with the Auth browser-session policy.

## Local run

```powershell
cd frontend
npm install
Copy-Item .env.example .env.local
npm run dev
```

For visual-only development, set `VITE_USE_MOCKS=true` in `.env.local`. Do not enable mocks in stage.

Run the release checks before deployment:

```powershell
npm run typecheck
npm test
npm run build
```

## Storefront experience

The customer storefront uses a warm, image-led homepage, collection shortcuts,
responsive product cards, and a product gallery. Its styling is scoped to
`.storefront-v2`; seller/admin screens use the separately scoped `.bo-shell` workspace design.

The catalogue loads 12 products at a time and automatically fetches another batch
near the bottom of the grid. Search, category, brand, price and sort remain in the
URL; changing them starts a new stream from page zero. Loaded products stay visible
if a later request fails. A retry/manual-load control supports recovery and browsers
without IntersectionObserver. The stream stops at the last page and deduplicates IDs.

Catalogue regression checks (Node 22.6+):

```powershell
npm test
```

Homepage photography uses HTTPS images from `images.pexels.com` and collection
assets from `cdn.dummyjson.com`, with local UI fallbacks when an image fails.
The living-room image is [Pexels photo 1571460](https://www.pexels.com/photo/interior-design-of-a-house-1571460/).

## Seller and admin workspaces

Both workspaces have responsive navigation, a shared table/form design, and product
draft previews. The seller overview uses real list totals; its visible/pending counts
are explicitly limited to the first 100 loaded records. The admin overview links to
available operational tools without inventing aggregate analytics.

The seller product table automatically loads batches of 12 as you scroll. Search is
labelled **Search loaded products**; refresh and successful archival restart the stream
to avoid skipping records after pagination shifts. Later failures retain loaded rows
and offer a retry. The final batch stops loading; a manual fallback remains available.

Order, user and payment tables retain server pagination. Their search is labelled
**Search this page** and filters only loaded records, not the full server catalogue.
Empty matches offer a clear-search action; refresh reloads the current server page.
Product forms keep ownership and validation rules. Refund, role, deletion and replay
handlers retain their existing safeguards; notification content remains redacted.

Seller **Bulk import** at `/seller/products/import` accepts a previewed JSON array
of 1..100 products owned by the signed-in seller. Editors use authenticated managed
detail endpoints, so hidden/archived products remain editable after a page reload.
Archive preserves history; enabling visibility and saving reactivates an eligible
seller's product. Admin catalogue operations include confirmed Product dead-letter
replay and cursor-based reconciliation, one batch of up to 100 products at a time.

The test command above includes workspace search and server-rendered markup checks.
These do not replace signed-in browser checks of mobile navigation, keyboard focus,
form interactions and table scrolling before deployment.

## Stage deployment

Deploy this folder as Vercel's project root. Set `VITE_API_BASE_URL` to the Gateway's public HTTPS URL and `VITE_USE_MOCKS=false`. Configure the same frontend origin in Gateway CORS, payment return configuration, and Auth email-link configuration.

Before a stage release, verify Gateway routing for public catalogue/Auth flows,
the guest-cart cookie domain/SameSite policy, `Idempotency-Key` CORS support, and
all `/seller/**` and `/admin/**` routes. The UI never falls back to service ports.

## Session security note

Access tokens are not persisted in localStorage or sessionStorage. On startup,
AuthProvider restores the session through the cookie-backed refresh endpoint;
protected routes wait for restoration. Refresh/logout requests contain no refresh
token JSON, and the API client includes browser credentials on Gateway requests.

Recent activity can trigger silent refresh before access expiry; authenticated
401 responses share one in-flight refresh and retry once. Failed refresh clears
local authentication. The inactivity countdown displays Continue session / Sign
out, and expiry signs out automatically. Auth remains authoritative for idle and
absolute expiry (default seven days); browser activity cannot extend that limit.
Stage/prod require HTTPS Secure cookies and a compatible frontend/Gateway SameSite
and CORS configuration.
