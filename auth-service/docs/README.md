# Auth Service Design

This folder documents the target production design for the replacement Auth Service. It is a design baseline; it does not describe the legacy implementation currently in this repository.

| Document | Purpose |
| --- | --- |
| [High-level design](hld.md) | Service boundaries, responsibilities, and component architecture. |
| [Low-level design](lld.md) | Modules, workflows, token lifecycle, and implementation rules. |
| [Database schema](schema.md) | PostgreSQL tables, relationships, indexes, and migration order. |
| [API contract](api.md) | Public endpoints, request/response behavior, and JWT compatibility. |
| [Events and operations](events-and-operations.md) | Kafka contracts, outbox processing, configuration, and security controls. |

## Compatibility contract

The replacement may change its internal schema and code, but must preserve these contracts until dependent services are migrated:

- JWT issuer and JWKS endpoint.
- `userId` JWT claim as a UUID string.
- `role` claim for current services, plus a `roles` array for the new design.
- Existing user UUIDs, because Order, Payment, Cart, Product, and Notification store them.
- `user-contact-updated` events used by Notification Service's recipient directory.

