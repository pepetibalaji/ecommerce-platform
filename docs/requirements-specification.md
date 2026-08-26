# E-Commerce Platform Requirements Specification

**Status:** Baseline derived from the current repository  
**Version:** 1.0  
**Updated:** 12 August 2026

## Purpose and scope

This is the requirements baseline for the event-driven e-commerce backend. It
separates **Implemented** behaviour from **Planned** (roadmap) and **Extended**
(production-completion) requirements. Scope includes backend APIs, internal
contracts, data, operations, and deployment; a customer web/mobile UI is out of
scope of the current repository.

## Product and actors

The platform lets customers discover products, manage carts, place and pay for
orders, and eventually track fulfilment. Administrators manage users, catalog,
inventory, orders, and refunds.

| Actor | Goal / permission |
| --- | --- |
| Guest | Browse the public catalog. |
| Customer | Register, authenticate, manage profile/cart, and manage owned orders/payments. |
| Administrator | Manage users, products, inventory, orders, and refunds. |
| Payment provider | Create checkout sessions and deliver signed webhooks. |
| Internal services/workers | Process gRPC/Kafka workflows and compensation. |
| Operations team | Configure, deploy, observe, and recover the platform. |

## Architecture and ownership

| Component | Data owned | Current responsibility |
| --- | --- | --- |
| API Gateway / Config Server | Route and runtime configuration | External routing, CORS, JWT validation, diagnostics, centralized config. |
| Auth Service | PostgreSQL users/refresh tokens; Redis blacklist | Identity, JWT, profile, and user administration. |
| Product Service | PostgreSQL catalog | Public catalog and admin catalog changes. |
| Cart Service | Redis cart records | Temporary customer carts. |
| Inventory Service | PostgreSQL inventory/reservations | Stock administration and idempotent reservations. |
| Order Service | PostgreSQL orders, inbox, release outbox | Order lifecycle and inventory compensation. |
| Payment Service | PostgreSQL payments, attempts, refunds, webhook events | Checkout, webhooks, refunds, payment outcomes. |

Clients use REST through the Gateway. Order calls Inventory through gRPC;
Kafka carries `order-created`, `payment-success`, and `payment-failed`. Each
service owns its data; cross-service database foreign keys are prohibited.

## Functional requirements — implemented

### Identity and access

| ID | Requirement | Acceptance condition |
| --- | --- | --- |
| FR-AUTH-01 | Guests shall register as active `CUSTOMER` users. | User is created with a BCrypt password and an access/refresh-token pair is returned. |
| FR-AUTH-02 | The service shall authenticate active users and issue JWTs. | JWT contains `sub`, `jti`, `userId`, `role`, `status`, `tokenVersion`, `iat`, and `exp`. |
| FR-AUTH-03 | The service shall rotate refresh tokens. | A valid refresh token returns a new pair and the old token cannot be reused. |
| FR-AUTH-04 | Users shall be able to log out and invalidate sessions. | Current JWT is blacklisted until expiry; supplied/all refresh tokens are revoked. |
| FR-AUTH-05 | Customers shall manage only their own profile. | `GET`/`PUT`/`DELETE /api/v1/users/me` use JWT ownership; deletion is soft and invalidates sessions. |
| FR-AUTH-06 | Admins shall manage users. | `ADMIN` can list/view, change role, soft-delete, and force logout; changes invalidate sessions. |
| FR-AUTH-07 | Resource services shall authorize by JWT claim. | `userId` enforces ownership and `role` maps to `ROLE_*`; cross-customer access is denied. |

### Catalog and cart

| ID | Requirement | Acceptance condition |
| --- | --- | --- |
| FR-PROD-01 | The system shall expose public paginated catalog and product-detail APIs. | `GET /api/v1/products` and `GET /api/v1/products/{id}` are publicly accessible. |
| FR-PROD-02 | Catalog listing shall filter by category and complete price range. | `category`, `minPrice`, `maxPrice`, `page`, and `size` produce the documented page. |
| FR-PROD-03 | Admins shall create, bulk-create, update, and delete products. | `/api/v1/admin/products/**` accepts only `ADMIN`. |
| FR-CART-01 | The system shall maintain one cart per authenticated user in Redis. | The key is `cart:{userId}`; callers cannot select another owner. |
| FR-CART-02 | Customers shall add, update, retrieve, remove, and clear cart items. | The documented `/api/v1/cart` operations work for JWT owner only. |
| FR-CART-03 | Adding an existing product shall increase its quantity. | Only one matching cart item remains with accumulated quantity. |
| FR-CART-04 | A cart shall expire after seven days of inactivity. | Each save refreshes a seven-day TTL; an empty cart key is removed. |

### Inventory

| ID | Requirement | Acceptance condition |
| --- | --- | --- |
| FR-INV-01 | Admins shall create, update, and view product inventory. | `/api/v1/admin/inventory` operations require `ADMIN`. |
| FR-INV-02 | Inventory shall expose `GetInventory`, `ReserveStock`, `ReleaseStock`, and `DeductStock` via gRPC. | Calls conform to the shared protobuf contract. |
| FR-INV-03 | Reservation-aware stock changes shall be idempotent. | Repeating the same reservation/product/quantity does not move stock twice. |
| FR-INV-04 | Reservation shall safely protect concurrent stock changes. | A locked local transaction reserves only sufficient stock, reducing available and increasing reserved stock. |
| FR-INV-05 | Release/deduction shall enforce state transitions. | `RESERVED → RELEASED` restores stock; `RESERVED → DEDUCTED` consumes it; conflicts fail. |

### Orders and payments

| ID | Requirement | Acceptance condition |
| --- | --- | --- |
| FR-ORD-01 | Customers shall create orders with shipping address, currency, and items. | A `PENDING` order is persisted only after every item is reserved. |
| FR-ORD-02 | Order creation shall call Inventory gRPC to check/reserve every item. | Each saved item has one stable reservation ID. |
| FR-ORD-03 | Order Service shall publish `order-created`. | Payment Service can idempotently prepare one pending payment per order. |
| FR-ORD-04 | Customers shall list, view, and cancel only their own orders. | JWT ownership is enforced; listing supports paging and optional status. |
| FR-ORD-05 | Admins shall list orders and update status. | Routes require `ADMIN`; cancellation queues stock release. |
| FR-ORD-06 | Order Service shall process payment outcome events idempotently. | Duplicate event IDs do not repeat transitions; pending success confirms and pending failure fails the order. |
| FR-ORD-07 | Payment failure/cancellation shall durably compensate inventory. | The status transaction saves one release-outbox command per reservation; a worker retries `ReleaseStock`. |
| FR-ORD-08 | Late/conflicting outcomes shall not overwrite terminal state. | Existing terminal state remains unchanged and event is acknowledged/recorded. |
| FR-PAY-01 | Payment Service shall idempotently create one payment from `order-created`. | Duplicate deliveries retain a single payment for the order. |
| FR-PAY-02 | Owners shall create or reuse checkout sessions. | Ownership is checked and an active provider checkout URL is returned. |
| FR-PAY-03 | Verified provider webhooks shall be payment-status authority. | Valid signed Stripe webhooks update payment state; invalid signatures do not. |
| FR-PAY-04 | Provider webhook delivery shall be deduplicated. | Provider/event uniqueness prevents duplicate state changes/outcomes. |
| FR-PAY-05 | Payment outcomes shall be published keyed by `orderId`. | Success sends `payment-success`; failure/cancellation sends `payment-failed` with correlation IDs. |
| FR-PAY-06 | Owners shall view own payments; admins shall query/refund payments. | Role/ownership checks apply and total refund value cannot exceed payment amount. |
| FR-PAY-07 | Stripe shall support checkout, verified webhooks, and refunds; Sandbox shall offer deterministic checkout. | Provider behavior matches the implemented adapter matrix. |

## Non-functional requirements — current baseline

| ID | Category | Requirement |
| --- | --- | --- |
| NFR-SEC-01 | Security | Passwords use BCrypt; business services are OAuth2 Resource Servers validating issuer/JWK material. |
| NFR-SEC-02 | Authorization | Public, authenticated, and admin endpoints are separated; ownership derives from JWT `userId`. |
| NFR-SEC-03 | Secrets | Runtime secrets/configuration must remain external to this source repository. |
| NFR-REL-01 | Data integrity | Services own isolated data; database changes are versioned with Flyway. |
| NFR-REL-02 | Consistency | Cross-service recovery uses local transactions, idempotent events/commands, Order inbox, and release outbox—not distributed transactions. |
| NFR-REL-03 | Recovery | Payment outcomes retry three times then enter `order-dlq`; inventory releases retry persistently until successful. |
| NFR-PERF-01 | Scalability | Stateless REST services, Kafka partitions, and Redis cart state enable horizontal scale through externalized config. |
| NFR-OBS-01 | Observability | Services expose health, info, metrics, and Prometheus endpoints and emit trace-aware JSON logs. |
| NFR-OBS-02 | Correlation | Use `traceId`, `spanId`, `correlationId`, `eventId`, `orderId`, and `paymentId` where relevant; do not use high-cardinality fields as metric/Loki labels. |
| NFR-OBS-03 | Monitoring | Local stack includes Prometheus, Grafana, Tempo, Loki, OTLP, Kafka exporter, dashboards, and payment-outcome alerts. |
| NFR-MAINT-01 | Maintainability | Use Java 21, Spring Boot, Maven modules, shared platform libraries, OpenAPI, and automated unit/controller tests. |
| NFR-DEP-01 | Deployability | Docker Compose provisions PostgreSQL, Redis, Kafka, and observability with health checks and persistent local volumes. |

## Planned and extended requirements

These requirements are not currently implemented. They combine the existing
roadmap with the reliability/security limitations recorded in the repository.

### Priority 1 — correctness, security, and resilience

| ID | Requirement | Done when |
| --- | --- | --- |
| ER-01 | Add transactional outboxes for Order `order-created` and Payment outcomes. | A database commit cannot lose its eventual Kafka event. |
| ER-02 | Persist signing keys/JWKs and externalize OAuth issuer/client config. | Auth restart does not invalidate verification keys and stage/prod issuer is configurable. |
| ER-03 | Hash refresh tokens and define platform-wide token revocation enforcement. | Stolen database values cannot be used as refresh tokens; logout/status/version policy is consistently enforced. |
| ER-04 | Define payment retry, cancellation, and refund policy. | A retry safely reorders or re-reserves/reactivates; confirmed cancellation has explicit refund behavior. |
| ER-05 | Restrict/audit admin confirmation bypass. | Confirmation needs verified provider evidence except for an auditable exceptional workflow. |
| ER-06 | Add creation-time orphan recovery and historical-order remediation. | Failed post-reservation creation persists compensation/reconciliation; older orders have a safe runbook. |
| ER-07 | Complete payment resilience. | `order-created` has retry/DLQ, unmatched webhooks reconcile, and Sandbox ingress/public returns work in test environments. |
| ER-08 | Add gRPC deadlines, service authentication where needed, and circuit breakers. | Downstream outages fail within configured bounds and are observable. |

### Priority 2 — complete core commerce

| ID | Requirement | Done when |
| --- | --- | --- |
| ER-09 | Build Notification Service. | It consumes domain events and records/delivers customer notifications. |
| ER-10 | Build Shipping/Fulfilment Service. | It assigns/tracks shipments, emits fulfilment events, and calls reservation-aware `DeductStock` after fulfilment commit. |
| ER-11 | Build Address Service. | Customers manage validated addresses; orders retain immutable address snapshots. |
| ER-12 | Build Pricing Service. | Coupons, discounts, server-side totals, and discount audit are applied at checkout. |
| ER-13 | Validate catalog and price authoritatively during checkout. | Orders do not trust client-supplied product existence, active state, currency, or price. |
| ER-14 | Integrate product, inventory, cart, and order lifecycle. | Catalog/inventory consistency, cart validation, and post-order cart behavior are defined and automated. |
| ER-15 | Complete Razorpay or remove it from advertised capability. | Checkout, webhook verification, status lookup, and refund work end-to-end, or endpoints/documentation are revised. |

### Priority 3 — experience and delivery maturity

| ID | Requirement | Done when |
| --- | --- | --- |
| ER-16 | Build Elasticsearch-backed Search Service. | Product search is indexed asynchronously with relevant filters. |
| ER-17 | Build Review Service. | Eligible customers can create moderated product ratings/reviews. |
| ER-18 | Enhance product lifecycle. | Images, currency, soft delete/active state, versioning, stock view, price history, and events are supported. |
| ER-19 | Add MFA, email verification, password reset, and account recovery. | Security and recovery flows are implemented with auditable policy. |
| ER-20 | Add an optional AI Service. | Recommendation/search features have consent, privacy, latency, and fallback policy. |
| ER-21 | Add Testcontainers integration and contract tests. | CI verifies database/Redis/Kafka/gRPC behavior and consumer/provider contracts. |
| ER-22 | Establish CI/CD quality gates. | Tests, static/security analysis, migration checks, image publishing, and promotion controls run automatically. |
| ER-23 | Prepare Kubernetes deployment. | Manifests/Helm include config/secrets, probes, autoscaling, and environment observability. |
| ER-24 | Add chaos and recovery testing. | Outbox/DLQ and database/Kafka/Inventory/provider outage recovery is proven with runbooks. |

## Core business rules

1. Customers access only data owned by their JWT `userId`.
2. Product reads are public; operational mutations require administrator role.
3. A cart is customer-scoped and expires after seven days without a save.
4. Each successfully reserved order item has one stable reservation ID.
5. A reservation has only one terminal state: `RELEASED` or `DEDUCTED`; payment-confirmed stock can remain `RESERVED` until fulfilment.
6. Each payment outcome event is applied at most once by Order Service.
7. After recovery, failed/cancelled orders must not retain intentionally unreleased reservations.
8. `CONFIRMED` means payment verified—not delivered.
9. A provider webhook, not a browser redirect, decides payment success/failure.

## Explicit current exclusions

Do not describe these as implemented: frontend UI, address book, shipping or
tracking, notifications, discounts, full-text search, reviews, MFA, email
verification, password reset, product images, authoritative checkout pricing,
automatic cart-to-order handoff, fulfilment deduction, or Razorpay integration.

## Change control and sources

Implementation work should reference these IDs in issues/PRs. A planned item
becomes implemented only after its acceptance condition has automated coverage
and, when integration-sensitive, an environment smoke test. Payment, refund,
cancellation, retention, privacy, and security policy changes require product
and operations approval.

Source baseline: [service documentation](services/README.md), [saga design](order-payment-inventory-saga-design.md), [local setup](local-setup.md), and [monitoring guide](../monitoring/README.md).
