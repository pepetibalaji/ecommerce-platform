# Inventory Service current implementation

## Implemented

* PostgreSQL inventory counters and reservation ledger managed by Flyway.
* Pessimistic row locking and transactional reservation-aware gRPC reserve/release/deduct semantics.
* Duplicate-safe retries using stable reservation UUIDs and `RESERVED`/`RELEASED`/`DEDUCTED` states.
* Admin and Product Service-verified seller REST management APIs.
* Kafka `product-created` consumer that creates zero-stock records idempotently with retry/DLT.
* gRPC error mapping, OpenAPI, Actuator, structured logs, Prometheus, and tracing dependencies.

## Important limitations

* REST direct available-stock updates can conflict with outstanding reservations; no adjustment ledger or invariant check prevents this.
* ID-less legacy gRPC operations are non-idempotent and must not be used by new callers.
* gRPC has no module-level authentication/authorization.
* Inventory's seller ID is null for manual REST creation; seller REST trust comes from Product Service, not the row.
* Product updates/deletes do not propagate to inventory.
* No low-stock policy/event, reservation expiration, fulfilment orchestration, or stock adjustment history is implemented.
