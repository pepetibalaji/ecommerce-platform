# Order Service low-level design

## Components

| Component | Responsibility |
| --- | --- |
| Customer, seller, and admin controllers | JWT ownership/role boundary; exposes command-oriented lifecycle endpoints rather than generic status mutation. |
| `OrderIdempotencyService` and repository | Claims `(userId, key)`, compares normalized request hashes, locks/replays existing work, and recovers uniqueness races. |
| `ProductSellerClient` | Bounded Product Service read; maps missing, unavailable, and dependency failures to stable checkout codes. |
| `InventoryGrpcClient` | Availability, reserve, and reservation-aware release calls. |
| `OrderServiceImpl` | Checkout orchestration, state transitions, immutable response mapping, and payment-event deduplication. |
| Order-created/refund outbox services and processors | Transactional Kafka hand-off, leased publishing, retry/backoff, and terminal-failure visibility. |
| Checkout-compensation and inventory-release processors | Durable release of uncertain/failed reservations using original reservation IDs. |
| Pending-payment expiry processor | Locks overdue pending orders, transitions them to `PAYMENT_EXPIRED`, and queues releases. |
| Payment outcome consumer | Consumes success, failure, refund completion, and refund rejection events into the persistent inbox. |
| Lifecycle audit and reconciliation services | Persist support evidence and return read-only outbox state counts. |

## Checkout transaction and compensation

The checkout request is normalized and hashed before remote work. The service claims the idempotency row first. A completed matching row returns its saved order; a changed hash raises `IDEMPOTENCY_KEY_REUSED`; a concurrent claim is recovered through the database unique constraint and row lock.

For each item, the service records the intended reservation ID before the gRPC reserve call. This covers the uncertainty where Inventory commits but the client receives a timeout. Once all reservations succeed, the order and `order_created_outbox` entry commit atomically. Kafka publication never participates in that database transaction.

If checkout throws after reservation attempts, `CheckoutCompensationService` runs in `REQUIRES_NEW` and persists one deduplicated release command per reservation. A best-effort immediate release may still happen, but correctness relies on the durable row. The processor locks eligible pending rows and retries failed releases up to the configured terminal bound.

## Lifecycle rules

- Only a `PENDING` order accepts payment success/failure or expiry.
- Customer cancellation of `PENDING` queues `CANCELLED` release work. Cancellation of `CONFIRMED` requests a full refund and moves to `REFUND_REQUESTED`.
- An admin refund request is full-payment only, requires a reason, and uses the same durable request/audit path. The Payment Service outcome—not Kafka publish success—decides the final refund state.
- A full refund completion queues an Inventory release only while the reservation remains releasable. A rejected, partial, or already-deducted result routes to `REFUND_REQUIRES_FULFILMENT_REVIEW` as needed.
- Payment consumers check `order_processed_events` first and lock the order. They record handled event IDs, including ignored late outcomes, so repeated delivery cannot overwrite state.

## Scheduling, locking, and time

Outbox repositories use database row leasing (`FOR UPDATE SKIP LOCKED`) so several Order Service instances can work safely. Publisher failures retain error text, attempt count, and next attempt timestamp; terminal rows stay visible for reconciliation. All entities/events use UTC `Instant` semantics, and the database migration converts legacy Order timestamps to `TIMESTAMP WITH TIME ZONE` using UTC.

Customer order pages are validated (`page >= 0`, `1 <= size <= 50`) and always sorted by `createdAt,desc`; callers cannot choose unbounded or nondeterministic customer history ordering.
