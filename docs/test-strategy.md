# Test Strategy

**Status:** Quality baseline and planned coverage  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Goals

Verify functional correctness, authorization, data integrity, contract
compatibility, and recovery behavior before changes reach production.

## 2. Test pyramid

| Level | Scope | Current / target |
| --- | --- | --- |
| Unit | Services, mappers, state transitions, validation. | Implemented across core services. |
| Controller | REST authorization, request validation, responses. | Implemented across core services. |
| Integration | JPA/Flyway, Redis, Kafka, gRPC, provider adapters. | Expand with Testcontainers. |
| Contract | OpenAPI, Kafka event, protobuf compatibility. | Planned CI requirement. |
| End-to-end | Gateway through order/payment/provider/inventory lifecycle. | Required in stage. |
| Resilience | Outage, replay, duplicate, DLQ, and recovery behavior. | Planned automation. |

## 3. Required coverage by domain

| Domain | Mandatory cases |
| --- | --- |
| Auth | Register/login/refresh rotation/logout, token version, status, role and ownership checks. |
| Product | CRUD, bulk create, pagination/filtering, admin access. |
| Cart | Owner isolation, quantity merge/update/remove/clear, expiry and concurrency behavior. |
| Inventory | Insufficient stock, concurrent reserve, duplicate reserve/release/deduct, invalid transitions. |
| Order | Create/reserve, cancellation, payment success/failure, duplicate/late outcome, release outbox retry. |
| Payment | Order-created idempotency, checkout reuse, valid/invalid/duplicate webhook, refund amount/idempotency. |
| Gateway | Route authorization, CORS, timeout/error behavior, rate-limit policy once enabled. |

## 4. Stage smoke suite

1. Register/login, browse catalog, create cart/order, and open checkout.
2. Deliver verified payment success: order becomes `CONFIRMED`, reservation stays `RESERVED`.
3. Deliver verified payment failure: order becomes `PAYMENT_FAILED`, release work completes, stock returns.
4. Replay webhook/event: no duplicate payment, order update, or stock movement.
5. Simulate Inventory outage during release then restore: worker retries and completes.
6. Verify unauthorized/customer-cross-owner/admin access is denied.
7. Check health, traces, metrics, consumer lag, alerts, and structured logs.

## 5. Test data and environments

Use generated/non-production users, product IDs, payment-provider test keys, and
isolated databases/topics. Never use production customer/payment data in local
or stage tests. Reset data using migrations and disposable Testcontainers or
environment-scoped datasets; do not delete shared environments blindly.

## 6. CI quality gates

Every pull request should compile, run unit/controller/integration tests,
validate Flyway migrations, run formatting/static analysis, dependency/container
and secret scans, and check OpenAPI/protobuf/event compatibility. Promotion
requires stage smoke success and no unresolved critical security issue.

## 7. Release evidence

Record build ID, migrations, test report, contract versions, deployment target,
smoke evidence, known risks, and rollback decision for each production release.
