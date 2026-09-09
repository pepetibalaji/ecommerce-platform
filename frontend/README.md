# Marketly frontend

React, TypeScript, and Vite frontend for the ecommerce platform.

## Scope

- Customer storefront: catalogue, guest/signed cart, auth, account security, checkout, payment verification, and orders.
- Seller workspace: owned product management, inventory, and seller-order views.
- Admin workspace: user, product, inventory, order, payment, and notification operations.

The seller/admin workspaces intentionally expose only documented backend actions.
They do not invent dashboards, catalogue/inventory search, fulfilment, payouts,
or customer-notification functionality where no API exists.

All browser requests use the API Gateway through `VITE_API_BASE_URL`; never expose a downstream service URL or secret in this app.

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
npm run build
```

## Stage deployment

Deploy this folder as Vercel's project root. Set `VITE_API_BASE_URL` to the Gateway's public HTTPS URL and `VITE_USE_MOCKS=false`. Configure the same frontend origin in Gateway CORS, payment return configuration, and Auth email-link configuration.

Before a stage release, verify Gateway routing for public catalogue/Auth flows,
the guest-cart cookie domain/SameSite policy, `Idempotency-Key` CORS support, and
all `/seller/**` and `/admin/**` routes. The UI never falls back to service ports.

## Session security note

The current Auth API returns bearer tokens. The frontend keeps the access token in session storage only to support the current backend contract; a BFF/HttpOnly-cookie session is the preferred production hardening path and should replace browser token storage when available.
