# Low-Level Design — E-Commerce Platform

**Status:** Current implementation design baseline  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Module layout

| Module | Implementation role |
| --- | --- |
| `gateway-service` | Reactive external HTTP entry point. |
| `config-server` | Spring Cloud Config server. |
| `auth-service` | Identity and JWT issuer. |
| `product-service` | Catalog API. |
| `cart-service` | Redis-backed cart API. |
| `inventory-service` | Inventory REST and gRPC operations. |
| `order-service` | Orders, Kafka outcome handling, inventory-release worker. |
| `payment-service` | Checkout, webhook, refund, and payment event API. |
| `common/*` | Security, exceptions, Redis, proto, gRPC, events, and tracing. |
| `monitoring` | Prometheus, Grafana, Tempo, Loki, and OTel configuration. |

## 2. Shared components

| Module | Responsibility |
| --- | --- |
| `common-security` | JWT claim constants, `userId` extraction, role converter, Resource Server support. |
| `common-exception` | Standard exceptions and JSON mapping for 400, 401, 404, and 409 responses. |
| `common-proto` | Inventory, Payment, and planned Shipping protobuf contracts. |
| `common-grpc` | Client factory, configuration, error mapping, and tracing interceptor. |
| `common-events` | Kafka topic constants and Order/Payment/Inventory event classes. |
| `common-redis` | Redis connection, lock/rate-limit support, and namespaced key helpers. |
| `common-tracing` | Trace response header auto-configuration. |

## 3. Service component design

### 3.1 Auth Service

```text
AuthController -> AuthService -> UserRepository / RefreshTokenRepository
UserController -> UserService -> UserRepository
AdminUserController -> UserService -> UserRepository
JwtTokenService -> RSA/JWK configuration
RefreshTokenService + TokenBlacklistService -> PostgreSQL / Redis
```

`users` stores identity, role, status, and `token_version`. `refresh_tokens`
stores user relation, token value, and expiry. Registration hashes passwords;
logout blacklists `jti` and revokes refresh tokens. User deletion, role change,
and forced logout increment token version.

### 3.2 Product and Cart Services

```text
ProductController -> ProductService -> ProductRepository -> products
CartController -> CartService -> CartRedisRepository -> cart:{userId}
```

`products` holds UUID, name, description, price, category, brand, and
timestamps. The list supports `page`, `size`, `category`, `minPrice`, and
`maxPrice`. A cart contains `userId`, items, and `updatedAt`; each item has
`itemId`, `productId`, and quantity. Saving a cart sets a seven-day TTL.

### 3.3 Inventory Service

```text
InventoryController -> InventoryService -> InventoryRepository
InventoryGrpcService -> InventoryService -> InventoryReservationRepository
```

| Table | Important fields | Behavior |
| --- | --- | --- |
| `inventory` | `product_id`, `available_stock`, `reserved_stock`, `updated_at` | One row per product; reservation changes lock this row. |
| `inventory_reservations` | `id`, `product_id`, `quantity`, `status`, timestamps | Stable reservation ID with `RESERVED`, `RELEASED`, `DEDUCTED` states. |

`ReserveStock`, `ReleaseStock`, and `DeductStock` accept product ID, quantity,
and reservation ID. Repeated matching requests are no-ops; mismatched payloads
or invalid transitions fail. `GetInventory` returns available/reserved stock.

### 3.4 Order Service

```text
OrderController / AdminOrderController -> OrderServiceImpl -> OrderRepository
OrderServiceImpl -> InventoryGrpcClient -> Inventory Service
OrderServiceImpl -> OrderEventPublisher -> Kafka order-created
PaymentOutcomeConsumer -> OrderServiceImpl
InventoryReleaseOutboxProcessor -> InventoryGrpcClient
```

| Table | Important fields | Purpose |
| --- | --- | --- |
| `orders` | id, user_id, total_amount, currency, status, payment_id, timestamps | Primary order state. |
| `order_items` | order_id, product_id, quantity, price, inventory_reservation_id | Order line and stable reservation reference. |
| `order_processed_events` | event_id unique, order_id, event_type | Payment outcome inbox/idempotency. |
| `order_inventory_release_outbox` | order/item/reservation IDs, reason, status, attempts, error | Durable inventory compensation command. |

Order creation validates request fields, reserves every item, persists a
`PENDING` order, then publishes `order-created`. Success transitions
`PENDING → CONFIRMED`; failure transitions `PENDING → PAYMENT_FAILED` and
inserts release-outbox records atomically. Cancellation uses the same outbox.
The worker starts after one second, runs every five seconds, locks batches of
25 using `FOR UPDATE SKIP LOCKED`, and leaves failed records pending for retry.

### 3.5 Payment Service

```text
PaymentOrderCreatedConsumer -> PaymentService -> PaymentRepository
PaymentController -> PaymentCheckoutService / PaymentQueryService
WebhookController -> PaymentWebhookService -> ProviderGateway -> event publisher
AdminPaymentController -> PaymentRefundService -> ProviderGateway
PaymentGrpcService -> payment service interfaces
```

| Table | Important fields | Purpose |
| --- | --- | --- |
| `payments` | id, order_id unique, user_id, amount, currency, status, provider, idempotency key | One payment per order. |
| `payment_attempts` | payment_id, provider session/intent IDs, status, expiry | Checkout attempt lifecycle. |
| `payment_refunds` | payment_id, amount, status, idempotency key | Refund state and total-value guard. |
| `payment_webhook_events` | payment_id, provider, provider_event_id, processing status | Delivery deduplication and audit. |

Payment moves from `PENDING` to `REQUIRES_CUSTOMER_ACTION`, then to
`PROCESSING`, `SUCCESS`, `FAILED`, or `CANCELLED`. Validated, deduplicated
webhooks update the state and publish an outcome. Stripe is implemented;
Razorpay remains adapter-only.

## 4. REST endpoints

| Domain | Main paths | Authorization |
| --- | --- | --- |
| Auth | `/api/v1/auth`, `/api/v1/users`, `/api/v1/admin/users` | Public auth endpoints; user JWT; admin role. |
| Product | `/api/v1/products`, `/api/v1/admin/products` | Public reads; admin mutation. |
| Inventory | `/api/v1/admin/inventory` | Admin. |
| Cart | `/api/v1/cart` | JWT owner. |
| Order | `/api/v1/orders`, `/api/v1/admin/orders` | JWT owner / admin. |
| Payment | `/api/v1/payments`, `/api/v1/admin/payments`, webhooks | Owner / admin / provider signature. |

Controllers apply bean validation. Domain services enforce ownership and
business transitions; `common-exception` creates consistent API error payloads.

## 5. gRPC contracts

| Service | RPC | Request | Active consumer |
| --- | --- | --- | --- |
| Inventory | `GetInventory` | product ID | Order Service. |
| Inventory | `ReserveStock` | product ID, quantity, reservation ID | Order Service. |
| Inventory | `ReleaseStock` | product ID, quantity, reservation ID | Order release worker. |
| Inventory | `DeductStock` | product ID, quantity, reservation ID | Future fulfilment service. |
| Payment | `ProcessPayment`, `RefundPayment`, `GetPaymentStatus` | Order/payment fields | Defined/served; no active Order-flow caller. |
| Shipping | `AssignShipment`, `UpdateShipmentStatus`, `GetShipment` | Shipment fields | Contract only; planned service. |

## 6. Kafka processing design

| Topic | Producer | Consumer | Key | Processing rule |
| --- | --- | --- | --- | --- |
| `order-created` | Order | Payment | `orderId` | Payment creates one idempotent payment. |
| `payment-success` | Payment | Order | `orderId` | Order inbox deduplicates and confirms a pending order only. |
| `payment-failed` | Payment | Order | `orderId` | Order inbox deduplicates, fails pending order, and inserts release work. |
| `order-dlq` | Order error handler | Operations/replay | Original key | Outcome processing retries three times first. |

Events carry event, correlation, and trace identifiers. All consumers must
handle at-least-once delivery safely.

## 7. Configuration and operations

Each service has a minimal local bootstrap `application.yml` and optionally
imports Config Server. Environment configuration supplies data connections,
issuer/JWK values, Kafka, payment provider settings, and Gateway routes.
Actuator exposes health/info/metrics/Prometheus; logs are trace-aware JSON.
Gateway uses a one-second connect timeout and five-second response timeout
(30 seconds for product bulk creation).

## 8. Current constraints and follow-up design work

- Order/Payment producer sends need transactional outboxes.
- Auth signing keys and refresh token storage require production hardening.
- Cart/checkout do not authoritatively validate product catalog, price, or stock
  beyond current Order-to-Inventory reservation checks.
- Product and Inventory lifecycles are not integrated; Cart has no concurrency control.
- Payment retry/refund/cancellation policy and fulfilment remain incomplete.
- Gateway rate-limit/circuit-breaker filters are not active; Config Server
  repository layout needs correction.

See [HLD](high-level-design.md) for system rationale and
[requirements specification](requirements-specification.md) for the roadmap.
