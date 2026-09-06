# Order Service current implementation

## Implemented

* Customer checkout with server-side catalog name/price/seller snapshots and shipping snapshot.
* Product active/availability validation and synchronous Inventory gRPC reservation with stable IDs.
* Customer ownership, admin order management, and seller-filtered item views.
* Payment success/failure/refund Kafka consumption with persistent event-ID deduplication.
* Inventory release outbox with scheduled retry, terminal failure, and manual-review states.
* PostgreSQL/Flyway persistence, OpenAPI, metrics, structured logging, and tracing dependencies.

## Important limitations

* `order-created` publication is asynchronous and not transactional with the order save; missed events require reconciliation.
* Checkout compensation release is best-effort; a failure there is logged rather than persisted as outbox work.
* `POST /orders` supports a per-user `Idempotency-Key` header, but legacy calls without it remain non-idempotent.
* Shipping data is caller-provided snapshot; no Address Service lookup/verification exists.
* Product/catalog changes after purchase do not alter immutable item snapshots.
* No fulfillment integration, shipment lifecycle, or automatic cart clearing is implemented.
