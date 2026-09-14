# Payment storage and migrations

PostgreSQL is the durable authority. JPA timestamps use `Instant`; all current database timestamp columns use `TIMESTAMPTZ`.

| Table | Constraints and purpose |
| --- | --- |
| `payments` | Unique Order ID and preparation idempotency key; immutable owner, amount and currency; versioned lifecycle and correlation/trace IDs. |
| `payment_attempts` | Per-attempt non-null idempotency key; immutable success/cancel URL snapshots and expiry; unique provider identifiers; partial unique index permits one CREATED/REQUIRES_CUSTOMER_ACTION/PROCESSING attempt per payment. |
| `payment_refunds` | Durable provider command and amount reservation; unique request and provider idempotency keys, provider identifiers, retry/lease fields, actor/reason/audit timestamps, reconciliation audit. |
| `payment_webhook_events` | Unique provider/event ID; raw-payload SHA-256, sanitized verified metadata, processing status, attempts, next retry and safe error code. No raw payment/card payload retention. |
| `payment_event_outbox` | Unique event ID and business outcome key, per-order sequence, complete event envelope, attempts, next retry, lease token/expiry, delivery timestamp and safe failure code. |
| `payment_cancellation_requests` | Durable command inbox/tombstone with Order totals, actor, reason, correlation/trace IDs, retry status and audit times; prevents checkout while cancellation/expiry is pending. |
| `payment_webhook_rate_limit` | Shared provider admission counters across replicas. |

Payment states: `PENDING`, `REQUIRES_CUSTOMER_ACTION`, `PROCESSING`, `SUCCESS`, `FAILED`, `CANCELLED`, `EXPIRED`, `REFUND_REQUESTED`, `REFUND_PROCESSING`, `REFUNDED`, `REFUND_FAILED`.

Refund states additionally distinguish `REFUND_MANUAL_REVIEW` from a verified terminal provider failure. An uncertain manual-review refund continues reserving its amount.

Outbox states are `PENDING`, `LEASED`, `DELIVERED`, `DEAD`. A unique `payment:{id}:result` business key prevents contradictory payment outcome records; refund completion/failure keys are scoped to the refund. Delivery may repeat after a publish/ack crash, so Order consumers must deduplicate event IDs and validate state transitions.

Migrations:

- V1/V2: original entities, provider uniqueness, correlation fields and idempotency.
- V3: legacy provider-check timestamp.
- V4: UTC migration, checkout request snapshots, required attempt keys, one-active-attempt uniqueness and payment EXPIRED.
- V5: payment outbox, verified webhook retry metadata and shared admission counters.
- V6: durable refund retry/reconciliation and cancellation command inbox/audit.

V4 interprets historical timezone-less timestamps as UTC. Confirm that historical deployments used UTC before rollout; data written in another zone requires a reviewed conversion. The active-attempt unique index deliberately fails if legacy duplicate active sessions exist. Reconcile those sessions with the provider before applying the migration; never delete payment evidence to force a migration through.
