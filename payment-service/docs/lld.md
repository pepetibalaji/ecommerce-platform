# Payment Service low-level design

| Component | Responsibility |
| --- | --- |
| Order-created consumer / TrustedOrderClient | Validate event schema/identity and signed Order identity, owner, total/currency, payable state and deadline; insert preparation idempotently. |
| PaymentCheckoutServiceImpl / CheckoutSessionTransactions | Commit an attempt reservation first; then acquire the Payment row lock and execute/replay its immutable provider request. |
| CheckoutUrlPolicy / PaymentProviderSafety | Validate approved provider hosts and frontend routes; prevent unsupported provider enablement. |
| VerifiedWebhookInbox / VerifiedWebhookProcessor | Persist unique verified safe envelope; lock Payment, validate provider/amount/metadata, apply allowed transition and enqueue outcome atomically. |
| PaymentOutboxStore / PaymentOutboxWorker | Store unique business outcomes in caller transaction; lease/retry delivery with per-order sequencing and durable DEAD state. |
| PaymentReconciliationWorker | Expire abandoned active attempts and backfill missing durable terminal outcomes. |
| PaymentRefundService / PaymentRefundWorkflow / PaymentRefundWorker | Reserve refundable funds, execute/reconcile provider work with leases and stable keys, escalate uncertainty, enqueue terminal outcomes. |
| PaymentCancellationService | Retain idempotent cancellation/expiry commands and audit data; block checkout; cancel unpaid or orchestrate remaining-balance refund. |
| Customer/admin controllers | Enforce ownership/roles, bounded pagination and safe response fields; expose audited operations recovery. |

All financial writers serialize on the Payment row before updating attempts/refunds. Payment also retains optimistic versioning. PostgreSQL uniqueness enforces one payment per order, one active attempt per payment, provider identifier ownership, refund idempotency and webhook replay protection.

Attempt reservation and provider execution use separate Spring transactions. If a provider accepts creation and local commit fails, the reservation survives with its key, URL snapshots and expiry. Retry replays the same provider request; signed metadata also allows the webhook worker to resolve the attempt before provider identifiers have been saved.

Payment/refund transition and outbox insertion use the same database transaction. Kafka delivery is at least once; the same event ID is reused after retries/crash recovery. Order consumers provide exactly-once effects with their inbox and lifecycle guards. No distributed transaction with the provider or Kafka is assumed.

See [schema and migration prerequisites](schema.md) and [the runbook](production-reliability.md).
