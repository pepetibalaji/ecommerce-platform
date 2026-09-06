# Order Service events and operations

## Kafka contracts

* Produces `order-created`, keyed by order UUID, after an order is persisted. The event contains order/user ID, total, currency, and product/quantity/price line events.
* Consumes `payment-success`, `payment-failed`, and `payment-refund-completed` through group `order-service-payment-outcomes` by default. Event ID is deduplicated in `order_processed_events`.
* `order-created` has no durable outbox/retry in this implementation: a Kafka send failure is logged but does not undo the order.

## Outbox operation

Release work is transactional with cancellation/payment/refund state updates. Processor defaults: initial delay 1s, fixed delay 5s, batch size 25, max 8 attempts; retry schedule is computed by `InventoryReleaseRetryPolicy`. Monitor pending/failed/manual-review rows, attempts, `last_error`, `next_attempt_at`, payment metrics, and logs. Manual review means stock was already deducted and release requires fulfillment reconciliation.

## Configuration and failure handling

| Setting | Default | Role |
| --- | --- | --- |
| `product-service.base-url` | `http://localhost:8082` | Catalog lookup target. |
| Product connect/read timeout | `1000`/`2000` ms | Fails checkout closed on catalog disruption. |
| `order.default-currency` | `INR` | Checkout currency default. |
| release fixed delay/batch/max attempts | `5000ms`/`25`/`8` | Release worker operation. |
| `OBSERVABILITY_LOG_FILE` | `../logs/order-service.json` | Structured log destination. |

PostgreSQL, Kafka, Inventory gRPC, JWT, Config Server, management, and tracing configuration are environment supplied. Public utility endpoints: `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, `/swagger-ui.html`.

Failure priorities: repair/reconcile missing `order-created` events, investigate failed/manual outbox rows, and use original reservation IDs for any inventory remediation. Do not release a deducted reservation outside fulfillment-approved procedure.
