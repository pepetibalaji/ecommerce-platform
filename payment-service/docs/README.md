# Payment Service documentation

Payment Service owns trusted payment preparation, durable checkout/refund work, verified provider outcomes, and reliable Order-facing event delivery.

| Document | Purpose |
| --- | --- |
| [API and frontend contract](api.md) | Owned endpoints, stable errors, bounded pagination and browser polling. |
| [Confirmation and recovery](confirmation-recovery.md) | Webhook-only confirmation and delayed-outcome recovery. |
| [Data model](schema.md) | UTC storage, uniqueness and V4-V6 migration prerequisites. |
| [Production reliability runbook](production-reliability.md) | Configuration, safe retries, provider enablement, refund review and incident procedures. |
| [Current implementation](current-implementation.md) | End-to-end service map, entry points, workers, lifecycle policies and release gates. |
| [High-level design](hld.md) | Service ownership and reliable lifecycle. |
| [Low-level design](lld.md) | Transactions, provider recovery and component responsibilities. |
| [Events and operations](events-and-operations.md) | Incoming commands, outgoing outcomes and delivery guarantees. |
| [Payment outcome schema](payment-outcome.schema.json) | Machine-readable schema for Order-facing payment/refund outcomes. |
| [Order integration contract](../../order-service/docs/payment-lifecycle.md) | Signed lookup, cancellation/refund policy, inventory effects and cross-topic consumption. |
| [Order command schema](../../order-service/docs/payment-command.schema.json) | Machine-readable cancellation, expiry and refund commands. |

Start with the current implementation for the whole service, then follow its links for endpoint, storage and operational details. These documents describe the implemented branch; live Stripe verification and infrastructure rollout are separate release gates.
