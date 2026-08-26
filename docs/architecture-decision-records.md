# Architecture Decision Records

**Status:** Baseline decision log  
**Version:** 1.0  
**Updated:** 12 August 2026

## ADR-001 — Domain-aligned microservices

**Status:** Accepted.  
**Decision:** Separate Auth, Product, Cart, Inventory, Order, and Payment into
services with independent data ownership.  
**Consequences:** Clear boundaries and deployability; integration and
eventual-consistency complexity must be managed explicitly.

## ADR-002 — Database per service

**Status:** Accepted.  
**Decision:** Each business service owns its PostgreSQL schema; no cross-service
foreign keys or direct database queries.  
**Consequences:** Independent schema evolution; references require API/event
validation and reconciliation.

## ADR-003 — OAuth2 Resource Server JWT security

**Status:** Accepted.  
**Decision:** Auth issues JWTs; Gateway/resource services validate using OAuth2
Resource Server and JWK support.  
**Consequences:** Shared identity contract; persistent keys and cross-service
revocation enforcement remain needed.

## ADR-004 — Redis for cart state

**Status:** Accepted.  
**Decision:** Store temporary customer carts under `cart:{userId}` with a
seven-day TTL.  
**Consequences:** Fast expiring state; cart is not a durable order source and
concurrent read-modify-write needs future hardening.

## ADR-005 — gRPC for Inventory commands

**Status:** Accepted.  
**Decision:** Order uses gRPC to get, reserve, release, and later deduct stock;
stable reservation IDs make commands idempotent.  
**Consequences:** Immediate stock response and clear protobuf contracts; add
deadlines, circuit breakers, and service authentication before production.

## ADR-006 — Kafka for payment workflow

**Status:** Accepted.  
**Decision:** Order publishes `order-created`; Payment emits outcomes; Order
consumes outcomes idempotently.  
**Consequences:** Decoupled workflow; transactional outboxes are required to
remove direct-producer publication gaps.

## ADR-007 — Inbox/outbox compensation

**Status:** Accepted.  
**Decision:** Order stores processed payment event IDs and writes Inventory
release commands atomically with failure/cancellation state changes.  
**Consequences:** Duplicate delivery and outage recovery is safe; workers and
operational monitoring are mandatory.

## ADR-008 — Observability-first operations

**Status:** Accepted.  
**Decision:** Use Actuator, Prometheus, Grafana, Tempo, Loki, OTLP, and
structured correlation fields.  
**Consequences:** Better cross-service diagnosis; labels must remain
low-cardinality and alerts need operational ownership.

## ADR process

Create a new ADR for changes to data ownership, public contracts, security,
consistency/retry semantics, infrastructure, or major technology. Include
context, options, decision, consequences, rollout, and reversal plan. Do not
rewrite accepted records; supersede them with a new ADR.
