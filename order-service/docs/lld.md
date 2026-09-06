# Order Service low-level design

## Components

| Component | Role |
| --- | --- |
| Controllers | Customer ownership validation, admin status management, seller-filtered views. |
| `ProductSellerClient` | Timed Product Service read; rejects missing/inactive/incomplete catalog product. |
| `InventoryGrpcClient` | Availability/reserve/release calls, mapping gRPC failures to application errors. |
| `OrderServiceImpl` | Checkout orchestration, status transitions, payment-event dedupe. |
| `OrderEventPublisher` | Async `order-created` send after persistence. |
| Payment consumer | Reads success/failure/refund Kafka topics. |
| Release outbox service/processor | Durable, idempotent deferred Inventory releases. |

## Checkout and compensation

`createOrder` builds items with a UUID reservation ID before remote reserve. It records each item in an attempted-reservations list **before** its gRPC call; if the client times out after Inventory commits, compensation still has the ID to release. The order is only saved after all reservations succeed. Catalog timeouts default to 1s connect/2s read. The order-created Kafka send is asynchronous after save and is not a transactional outbox.

## State and idempotency

Payment consumers lock the order by ID, first check `order_processed_events.event_id`, and record each handled event. Duplicate IDs are ignored. Late events that do not match the expected active state are logged/recorded without changing state. Customer/admin cancellation locks the order and inserts release work, protected by unique reservation ID.

The release worker runs every 5 seconds by default, locks eligible pending rows, and calls reservation-aware Inventory release. Success becomes `COMPLETED`; failures schedule exponential retry (default max 8). A release rejected because reservation was already deducted becomes `MANUAL_REVIEW`; exhausted retries become `FAILED`.

Seller query selects orders by matching seller ID and filters each response to that seller's lines, calculating a seller-only subtotal. An ADMIN using this route is still scoped to their JWT `userId`, not a platform-wide seller list.
