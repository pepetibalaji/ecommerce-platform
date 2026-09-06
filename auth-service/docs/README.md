# Auth Service Design

This folder documents the production design and implemented baseline for the replacement Auth Service. It supersedes the legacy Auth model; this service is greenfield and does not migrate legacy identity records.

| Document | Purpose |
| --- | --- |
| [High-level design](hld.md) | Service boundaries, responsibilities, and component architecture. |
| [Low-level design](lld.md) | Modules, workflows, token lifecycle, and implementation rules. |
| [Database schema](schema.md) | PostgreSQL tables, relationships, indexes, and migration order. |
| [API contract](api.md) | Public endpoints, request/response behavior, and JWT compatibility. |
| [Events and operations](events-and-operations.md) | Kafka contracts, outbox processing, configuration, and security controls. |
| [Contract verification and external configuration](contract-verification.md) | Current cross-service JWT/event dependencies, deployment placeholders, and release blockers. |

## Compatibility contract

The replacement may change its internal schema and code, but must preserve these contracts until dependent services are migrated:

- JWT issuer and JWKS endpoint.
- `userId` JWT claim as a UUID string.
- `role` claim for current services, plus a `roles` array for the new design.
- `userId` values must remain UUID strings so Order, Payment, Cart, Product, and Notification can parse new identities correctly.
- `user-contact-updated` events used by Notification Service's recipient directory.

