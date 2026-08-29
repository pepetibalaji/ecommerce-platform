# API Specification

**Status:** Current REST API baseline  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Conventions

- Base path: `/api/v1`; clients normally use API Gateway at `http://localhost:8080`.
- JSON request/response payloads; UUIDs identify resources; ISO-4217 currency
  values are uppercase three-letter strings.
- Protected endpoints use `Authorization: Bearer <access-token>`.
- JWT `userId` controls customer-owned resources; `ADMIN` controls admin APIs;
  `SELLER` controls seller-owned catalog, inventory, and fulfilment views.
- List endpoints use zero-based `page` and `size`; responses use the service's
  Spring page representation.
- Successful create operations return `201`; deletion normally returns `204`.
- Exact request/response fields are published in each running service's OpenAPI
  document. This document is the cross-service contract index.

## 2. Error model

Shared exception handling returns the platform API error structure. Consumers
must treat HTTP status as authoritative.

| Status | Meaning | Typical cause |
| --- | --- | --- |
| 400 | Bad request | Validation, invalid state transition, malformed request. |
| 401 | Unauthorized | Missing/invalid/expired token. |
| 403 | Forbidden | Customer accessing another owner's resource or non-admin admin request. |
| 404 | Not found | Missing product, inventory record, order, or payment. |
| 409 | Conflict | Duplicate resource or incompatible business state. |
| 500 | Server error | Unexpected platform or downstream error. |

## 3. Authentication and user API

| Method | Path | Access | Request / result |
| --- | --- | --- | --- |
| POST | `/auth/register` | Public | Registration data; returns token pair and user information. |
| POST | `/auth/login` | Public | Credentials; returns token pair. |
| POST | `/auth/refresh` | Public with refresh token | Refresh token; rotates and returns token pair. |
| POST | `/auth/logout` | JWT | Revokes refresh token(s) and blacklists current JWT. |
| GET | `/users/me` | JWT | Current user profile. |
| PUT | `/users/me` | JWT | Current user profile update. |
| DELETE | `/users/me` | JWT | Soft-delete own account and invalidate sessions. |
| GET | `/admin/users` | Admin | Paginated users. |
| GET | `/admin/users/{id}` | Admin | User detail. |
| DELETE | `/admin/users/{id}` | Admin | Soft-delete user. |
| PUT | `/admin/users/{id}/role` | Admin | Change role and invalidate sessions. |
| POST | `/admin/users/{id}/logout` | Admin | Force user logout. |

JWT access tokens contain `sub` (email), `jti`, `userId`, `role`, `status`,
`tokenVersion`, `iat`, and `exp`. Clients must never infer customer identity
from a path/body field when a JWT is available.

## 4. Catalog and inventory API

| Method | Path | Access | Request / result |
| --- | --- | --- | --- |
| GET | `/products?page=&size=&category=&minPrice=&maxPrice=` | Public | Paginated catalog; price range applies when both bounds are present. |
| GET | `/products/{productId}` | Public | Product detail. |
| POST | `/admin/products` | Admin | Create product. |
| POST | `/admin/products/bulk` | Admin | Bulk-create products. |
| PUT | `/admin/products/{productId}` | Admin | Update product. |
| DELETE | `/admin/products/{productId}` | Admin | Hard-delete product. |
| POST | `/seller/products` | Seller/Admin | Create a product owned by the authenticated seller. |
| GET | `/seller/products` | Seller/Admin | Paginated products owned by the authenticated seller. |
| PUT/DELETE | `/seller/products/{productId}` | Seller/Admin | Seller may mutate only their own product; admin may mutate any product. |
| POST | `/admin/inventory` | Admin | Create stock record for product UUID. |
| PUT | `/admin/inventory/{productId}` | Admin | Update stock record. |
| GET | `/admin/inventory/{productId}` | Admin | Available/reserved stock view. |
| POST | `/seller/inventory` | Seller/Admin | Create stock only for a seller-owned product. |
| GET/PUT | `/seller/inventory/{productId}` | Seller/Admin | Read/update stock only for a seller-owned product. |

## 5. Cart and order API

| Method | Path | Access | Request / result |
| --- | --- | --- | --- |
| POST | `/cart` | JWT | Add item or increase quantity for product already in cart. |
| GET | `/cart` | JWT | Current customer's cart. |
| PUT | `/cart/{itemId}` | JWT | Replace selected item quantity. |
| DELETE | `/cart/{itemId}` | JWT | Remove cart item. |
| DELETE | `/cart` | JWT | Clear own cart. |
| POST | `/orders` | JWT | Create a pending order after inventory reservation. |
| GET | `/orders?status=&page=&size=` | JWT | Paginated owned orders. |
| GET | `/orders/{id}` | JWT | Owned order detail. |
| PUT | `/orders/{id}/cancel` | JWT | Cancel permitted order and queue stock compensation. |
| GET | `/admin/orders?status=&page=&size=` | Admin | Paginated all-order view. |
| PUT | `/admin/orders/{id}/status` | Admin | Administrative status transition. |
| GET | `/seller/orders?page=&size=` | Seller/Admin | Seller-only order items and fulfilment address; no customer or payment data. |

Order create input includes shipping address, item product IDs, quantities,
prices, and currency. Current implementation validates request shape and stock;
catalog/price validation will be added as an extended requirement.

## 6. Payment API

| Method | Path | Access | Request / result |
| --- | --- | --- | --- |
| POST | `/payments/orders/{orderId}/checkout-session` | JWT owner | Create/reuse checkout session and return provider URL. |
| GET | `/payments/me` | JWT | Paginated owned payments. |
| GET | `/payments/orders/{orderId}` | JWT owner | Owned payment by order. |
| GET | `/payments/{paymentId}` | JWT owner | Owned payment detail. |
| GET | `/admin/payments` | Admin | Paginated payment list. |
| GET | `/admin/payments/{paymentId}` | Admin | Payment detail. |
| POST | `/admin/payments/{paymentId}/refund` | Admin | Request refund; total may not exceed payment amount. |
| POST | `/payments/webhooks/stripe` | Stripe signature | Verified Stripe event ingress. |
| POST | `/payments/webhooks/razorpay` | Razorpay signature | Route exists but adapter is not implemented. |
| GET | `/public/payments/success` | Public | Provider return page endpoint. |
| GET | `/public/payments/cancel` | Public | Provider return page endpoint. |

Webhook routes are provider-to-service calls; they must not use user JWT
authorization. The signed webhook, not the return page, decides payment state.

## 7. Idempotency, tracing, and versioning

- Provider webhook idempotency is internal, keyed by `(provider, providerEventId)`.
- Payment preparation uses order/idempotency uniqueness; checkout reuses active
  unexpired attempts.
- Customer order create does not currently expose an HTTP idempotency-key
  contract. Adding `Idempotency-Key` is recommended before public production use.
- Clients should preserve `X-Trace-Id` returned by the platform for support.
- Breaking API changes require a new versioned path or explicit compatibility
  window; additive optional fields are preferred.

## 8. OpenAPI and service endpoints

Local Swagger UIs: Auth `8081`, Product `8082`, Inventory `8084`, Cart `8085`,
Order `8086`, and Payment `8087`, each at `/swagger-ui.html`. Gateway aggregates
`/{service}/v3/api-docs` where route configuration is available.

See [event and gRPC contracts](event-grpc-contracts.md) for non-REST interfaces.
