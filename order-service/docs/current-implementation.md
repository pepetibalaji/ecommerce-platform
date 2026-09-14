# Order Service current implementation

## Delivered reliability behavior

- Public checkout requires a payload-bound `Idempotency-Key`; idempotency records are customer-scoped, retained for 24 hours by default, and protected from cross-node races by the database.
- Checkout persists the order, immutable item/address snapshots, idempotency completion, and an `order_created_outbox` row in one transaction.
- The order-created publisher leases rows with `FOR UPDATE SKIP LOCKED`, publishes with the order ID as Kafka key, retries with backoff, and retains terminal failures for reconciliation.
- Failed or uncertain post-reservation checkout work creates durable checkout-compensation rows using the original reservation IDs. A retrying worker releases those reservations safely.
- Pending payment expires after the configured 15-minute default. The expiry worker locks eligible rows, moves them to `PAYMENT_EXPIRED`, and queues inventory release work.
- Payment outcome consumption uses a persistent event-ID inbox. Duplicate and late success/failure events do not overwrite a terminal lifecycle state.
- Pending cancellation releases reservations through the durable release outbox. Confirmed-order cancellation and admin full-refund requests enter `REFUND_REQUESTED`, write a durable refund-request outbox row, and remain pending until a Payment Service outcome is consumed.
- Full refund completion queues release only where Inventory still has a reservation. Unsafe or partial outcomes become `REFUND_REQUIRES_FULFILMENT_REVIEW` instead of releasing deducted stock.
- Lifecycle decisions are recorded in append-only application audit entries. Admin users can inspect audit history and outbox reconciliation counts through read-only endpoints.
- Customer pagination is bounded and deterministically sorted; public DTOs and persisted Order-side timestamps use `Instant`/PostgreSQL `TIMESTAMP WITH TIME ZONE`.

## Important operational boundaries

- Order Service owns the purchase record and snapshots, not Product, Inventory, Payment execution, shipment, fulfilment, or saved-address data.
- There is no Address Service. A shipping address is caller-supplied at checkout and retained as the order snapshot.
- A `MANUAL_REVIEW` inventory-release row means Inventory reports the reservation was already deducted; fulfilment/operations must decide the physical-stock outcome.
- The administrative HTTP API deliberately has no generic status-change endpoint. The retained Java method is source compatibility only and must not be exposed by a new controller route.
- The service provides a reconciliation snapshot, not an automatic operator replay endpoint. Terminal rows require investigation and controlled remediation.
- Focused unit/service tests and compilation pass, but the ticket still needs real PostgreSQL/Kafka integration coverage for concurrent idempotency, publisher failure recovery, inventory failure, expiry, refund, and late-event scenarios before production sign-off.

See [Checkout reliability contract](checkout-reliability.md), [API and contracts](api.md), and [Events and operations](events-and-operations.md) for the public and operational details.
