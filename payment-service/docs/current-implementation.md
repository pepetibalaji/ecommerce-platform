# Current Payment Service implementation

This is the service overview for `feat/payment-production-reliability`. It describes implemented application behavior as of 2026-09-14. Detailed contracts live in the linked documents; it does not assert that the branch has been deployed or verified against a live Stripe account.

## Ownership, runtime and entry points

Payment owns provider sessions, attempts, payment/refund records, verified webhook processing and outgoing payment outcomes. Order owns immutable order totals, the payable deadline, cancellation eligibility, fulfilment and inventory effects. Payment does not price browser-provided items or directly release stock.

The runtime is Java 21 and Spring Boot with PostgreSQL/JPA/Flyway, Kafka, OAuth2 JWT, Config Server, Actuator and OpenAPI. Environment configuration is maintained in `C:\e-com\ecommerce-config-repo`; the local application file selects the profile and Config Server. REST normally runs on port 8087.

| Entry point | Implemented behavior |
| --- | --- |
| Customer REST | Owned checkout creation/reuse, lookup by order/payment ID and paginated history. The compatibility refresh POST only reads persisted state. |
| Admin REST | Payment search/detail, durable full/partial refund requests, audited refund reconciliation, outbox/webhook status and safe replay. |
| Stripe webhook | Public HTTP admission followed by verification of the signature over the exact raw body; no customer JWT is required for this provider callback. |
| Order Kafka commands | Validated Order-created, cancellation/expiry and refund requests with durable idempotency and bounded consumer retries/dead-letter recovery. Broker authentication and ACLs are required. |
| Legacy browser routes | `/public/payments/success` and `/cancel` send a deprecated 303 redirect to an approved frontend route without changing state. |
| Legacy gRPC | `processPayment`, `refundPayment` and `getPaymentStatus` return `FAILED_PRECONDITION`; callers must use the authenticated APIs or durable Order commands. |

Customer APIs use the JWT `userId` for ownership; admin routes require `ADMIN`. See the [API reference](api.md) for exact paths, request/response fields and safe error codes, and [the runbook](production-reliability.md) for operations endpoints.

## Preparation and checkout

Payment preparation checks the Order-created schema and required fields, then validates identity, owner, immutable total/currency, payable state and deadline against a signed internal Order lookup. Matching duplicate events reuse the prepared payment; inconsistent duplicates are rejected. Customer APIs cannot construct arbitrary payments. The [Order integration contract](../../order-service/docs/payment-lifecycle.md) specifies the signed request and response.

Checkout commits an attempt reservation before a separate locked provider-execution transaction. The reservation preserves the provider idempotency key, return URLs and expiry across browser retries, timeouts and database rollback. A shared Payment lock and database active-attempt uniqueness prevent concurrent sessions. Verified webhook metadata can recover provider identifiers if creation succeeded before local persistence failed. Eligible retries return the existing session; processing, terminal, refund-state and cancellation-pending payments cannot start another checkout.

Provider returns target the authenticated frontend `/payment/return` route. The browser polls Order and Payment GET endpoints; redirects and URL parameters never prove payment. Preparation allows five calls per user attempt, with retries only for the structured `PAYMENT_PREPARING` response. Return polling is bounded to 25 checks, followed by an explicit status retry. Checkout URLs and offset-aware expiry are validated on both sides.

## Verified outcomes, expiry and delivery

Only verified provider webhooks move a payment to `SUCCESS` or `FAILED`. The service caps request bytes, applies shared provider rate limits, verifies the raw-body signature with configured rotation overlap, and retains a payload hash plus a minimal safe envelope. A unique provider/event ID prevents replay effects; a changed payload under the same ID raises an anomaly. The retained verified inbox commits before processing so unresolved or failed transitions remain recoverable.

Webhook processing locks Payment before changing attempts or refunds and validates provider identifiers, metadata, amount and currency. The transition, outgoing outcome and successful inbox processing commit together. Late success after an unpaid terminal state requires manual review; it cannot silently resurrect Payment or Order. Authenticated provider refund responses may resolve refund work; provider payment-status lookup cannot confirm a charge.

Local expiry locks eligible `PENDING` or `REQUIRES_CUSTOMER_ACTION` payments and expires abandoned active attempts without waiting for provider delivery. It does not expire a `PROCESSING` charge. An Order deadline with no checkout attempt is resolved through the durable Order expiry command. Order releases inventory only after an authoritative outcome permits it.

Payment/refund transitions and outgoing outcomes share a database transaction. The outbox leases rows with `SKIP LOCKED`, retries with bounded backoff and retains exhausted records as `DEAD`. An earlier undelivered event blocks later publication for the same order. This orders publication attempts, not consumption across different Kafka topics. Delivery is at least once; Order consumers use event IDs and lifecycle checks for exactly-once effects. Reconciliation inserts missing durable outcomes; existing pending/dead records retain their IDs for delivery/replay.

See [confirmation and recovery](confirmation-recovery.md), [event contracts](events-and-operations.md) and the [outcome schema](payment-outcome.schema.json).

## Refund and cancellation policy

Refund requests reserve amounts under the Payment lock before provider execution. Successful, pending and uncertain/manual-review refunds count against the refundable balance. Workers replay the saved provider key after uncertain acceptance, retrieve known provider refund IDs, clear leases on terminal results and escalate bounded/exhausted work to `REFUND_MANUAL_REVIEW`. An operator can request audited reconciliation under the safe replay policy without changing the original key or request audit.

Partial refunds are admin operations. Payment has no `PARTIALLY_REFUNDED` enum: when a partial refund completes and no other work/review remains, its aggregate status is `SUCCESS`; refund rows retain the amounts and Order displays `PARTIALLY_REFUNDED`. Full cumulative completion produces `REFUNDED`. Pending work and uncertain acceptance have distinct refund states; uncertainty retains its amount reservation.

| Cancellation trigger | Implemented policy |
| --- | --- |
| Pending Order | Retain a durable cancellation command and block checkout. Cancel an unpaid payment, or wait for a processing charge's verified outcome. |
| Pending cancellation races with success | The original Payment cancellation worker requests the remaining balance under the Payment lock. |
| Confirmed Order | Order requests a refund for the entire immutable total and remains in an intermediate state until the refund outcome. |
| Partially refunded Order | Refuse automatic customer cancellation; an admin must investigate the remaining balance. A competing partial refund can move a full cancellation request to review. |
| Stock already deducted/shipped | Order/inventory processing requires fulfilment review; Payment does not automatically restock inventory. |

Cancellation commands preserve actor, reason, correlation/trace IDs and audit times. A missing prepared payment can be recovered through trusted Order lookup using the persisted cancellation command. Commands waiting on preparation/provider resolution retry every 30 seconds and enter manual review after 24 hours from receipt. Full policy and inventory transitions are in the [Order lifecycle document](../../order-service/docs/payment-lifecycle.md).

## Background work and durable storage

| Worker | Work and default schedule |
| --- | --- |
| `PaymentOutboxWorker` | Lease and publish outgoing events every 1 second; retain delivery/retry/dead state. |
| `PaymentReconciliationWorker.expire` | Expire eligible abandoned attempts every 15 seconds. |
| `PaymentReconciliationWorker.reconcile` | Repair missing payment/refund outcomes and refresh the dead-count metric every 60 seconds. |
| `VerifiedWebhookRetryWorker` | Retry retained verified unresolved/failed envelopes every 5 seconds, subject to attempt bounds. |
| `PaymentRefundWorker` | Execute/reconcile leased refund work every 5 seconds. |
| `PaymentCancellationRequestedConsumer.processPending` | Process up to 50 due cancellation/expiry commands every 5 seconds. |

The configuration provides four scheduler threads. Durable tables hold payments, attempts, refunds, webhook inbox records, outcome outbox records, cancellation commands and shared webhook admission counters. Entities/API timestamps use `Instant`; database timestamps use `TIMESTAMPTZ`. Pagination requires nonnegative pages, sizes 1-50 and deterministic `createdAt DESC, id DESC` sorting. See [schema](schema.md) for states, constraints and migration prerequisites.

## Provider support, operations and verification

Stripe implements checkout, signed payment/refund events and refund creation/retrieval. Enabling it outside development requires the staging-verification flag. Razorpay remains disabled. Sandbox is limited to explicit dev/local/test profiles and has no public sandbox webhook route. Its mock checkout return cannot settle a payment, and its refund creation/retrieval returns processing until a test fixture supplies a terminal result. Deterministic provider fixtures do not establish real payment acceptance.

Metrics and structured logs cover delivery, retries, signature failures, unresolved events, conflicting transitions, duplicate anomalies and refund/cancellation review. Admin operations expose durable outbox/webhook status and audited retry actions. Customer responses exclude raw provider failure text, secrets and webhook data. See [the production runbook](production-reliability.md) for configuration, alerts and incident procedures.

The full backend Maven `verify` run passed 460 tests across the service reactor, including PostgreSQL, Kafka and MongoDB integration tests. Frontend validation includes 95 tests and a production build with mocks disabled. Application CI requires the payment database suites to run without skips and retains test reports; configuration CI checks all 27 environment YAML files and Payment/Order integration settings. Stripe calls are mocked in automated tests. Production credentials, Kafka ACLs/TLS/SASL, signed Order lookup secrets, exact HTTPS origins, migration review, deployment rollout and a real staging checkout/refund exercise remain deployment responsibilities.
