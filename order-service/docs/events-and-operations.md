# Order Service events and operations

## Kafka contracts

| Direction | Topic | Reliability and handling |
| --- | --- | --- |
| Produces | `order-created` | A transactional `order_created_outbox` row is written with the order. The publisher sends with order ID as key and Payment Service must remain idempotent because delivery is at least once. |
| Produces | `payment-refund-requested` | A full-refund command is written to `order_refund_request_outbox` before publishing. Kafka acknowledgement means delivery to Kafka only; the order remains `REFUND_REQUESTED` until an outcome event is consumed. |
| Produces | `order-completed` | Emitted after a successful payment confirmation for downstream consumers. Consumers must treat it as at-least-once. |
| Consumes | `payment-success`, `payment-failed` | Event ID is recorded in `order_processed_events`. Only an active `PENDING` order changes state; duplicate or late events are recorded/ignored safely. |
| Consumes | `payment-refund-completed`, `payment-refund-request-rejected` | Applies or refuses the requested refund workflow. Full safe completion may enqueue release work; partial, unsafe, or rejected outcomes are auditable and may require fulfilment review. |

Kafka producers use `acks=all`, idempotent producer support, and configured retries. Those producer settings complement—not replace—the application outboxes.

## Durable work and reconciliation

| Work family | States | Operations meaning |
| --- | --- | --- |
| `order_created_outbox` | `PENDING`, `PUBLISHED`, `FAILED` | `FAILED` means Payment Service initiation may not have been requested. Investigate before any controlled replay. |
| `checkout_compensation_outbox` | `PENDING`, `COMPLETED`, `FAILED` | Releases a reservation made before checkout could complete. It always uses the original reservation ID. |
| `order_inventory_release_outbox` | `PENDING`, `COMPLETED`, `FAILED`, `MANUAL_REVIEW` | Releases reservation stock after payment failure, expiry, cancellation, or full refund. `MANUAL_REVIEW` is not safe to auto-release. |
| `order_refund_request_outbox` | `PENDING`, `PUBLISHED`, `FAILED` | Tracks the request sent to Payment Service. `PUBLISHED` is not refund completion. |

`GET /api/v1/admin/orders/reconciliation/outboxes` returns a read-only count snapshot for all four families. Alert on nonzero or growing `FAILED` and `MANUAL_REVIEW` counts, and on age/backlog of `PENDING` work. Use the linked order, reservation, payment, Kafka event, and trace identifiers in structured logs for an investigation; do not manually release inventory without the original reservation ID and fulfilment approval.

## Scheduled processors

| Processor | Default cadence / bounds | Work |
| --- | --- | --- |
| Order-created outbox | initial 1s, then every 5s; batch 25; max 8 attempts | Publishes payment-initiation events with exponential retry capped at five minutes. |
| Refund-request outbox | initial 1s, then every 5s; batch 25; max 8 attempts | Publishes full-refund commands with exponential retry capped at five minutes. |
| Inventory-release outbox | initial 1s, then every 5s; batch 25; max 8 attempts | Calls Inventory release and routes already-deducted reservations to manual review. |
| Checkout compensation | every 5s; batch 25; max 8 attempts | Releases reservations from an unsuccessful checkout transaction. |
| Pending-payment expiry | every 60s; batch 25 | Locks overdue pending orders, expires them, and queues release work. |

All workers lease eligible rows, so multiple service instances can process independent work without double-processing the same row. Values are configurable; see the deployed Config Server environment files rather than assuming source defaults in production.

## Configuration

| Setting | Default | Role |
| --- | --- | --- |
| `order.checkout.pending-payment-expiry` | `15m` | Maximum lifetime of a pending payment reservation. |
| `order.checkout.idempotency-retention` | `24h` | Retention window for completed/processing idempotency keys. |
| `order.checkout.max-quantity-per-product` | `100` | Per-product checkout limit; product-specific overrides are supported. |
| `order.checkout.max-total-quantity` | `500` | Total quantity limit for one checkout. |
| `order.order-created-outbox.*` | batch 25, max 8 attempts | Publisher scheduling, batching, and terminal retry bound. |
| `order.checkout-compensation.*` | batch 25, max 8 attempts | Reservation compensation scheduling and retry bound. |
| `order.refund-request-outbox.*` | batch 25, max 8 attempts | Refund-request publisher scheduling and retry bound. |
| `order.inventory-release.*` | batch 25, max 8 attempts | Reservation-release scheduling and retry bound. |
| `order.payment-expiry.*` | batch 25 | Expiry-worker batching and scheduling. |

PostgreSQL, Kafka, Inventory gRPC, JWT, Config Server, tracing, and management settings are environment supplied. The production Order Service configuration sets Hibernate JDBC timezone to UTC. Public utility endpoints are `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, and `/swagger-ui.html`.

## Incident checklist

1. Confirm the affected order status, payment ID, and lifecycle audit entries.
2. Read the reconciliation snapshot and inspect the relevant durable row before changing anything.
3. For unpublished `order-created` or refund-request work, validate Kafka and the receiving service before controlled recovery.
4. For inventory failures, retain the reservation ID and never issue a new arbitrary release command.
5. Treat `MANUAL_REVIEW` and `REFUND_REQUIRES_FULFILMENT_REVIEW` as fulfilment incidents, not transient retry failures.
6. Preserve trace/event IDs when escalating to Payment, Inventory, or platform operations.
