# Inventory Service operations

Operational configuration, product-created retry/DLT behavior, metrics, and recovery are maintained in [Events and operations](events-and-operations.md).

## Production controls

Set `INVENTORY_GRPC_REQUIRE_MTLS=true`, configure server trust/key material in the platform's secret-backed gRPC configuration, and allow ingress only from the service mesh/order-service network identity. Clients must include `x-internal-caller: order-service`; failed authorization is metered.

Kubernetes deployment templates are provided in [`deploy/kubernetes`](../../deploy/kubernetes): apply `inventory-grpc-networkpolicy.yaml` and render `inventory-grpc-tls-secret.example.yaml` from the production secret manager. The private key example is deliberately non-functional; platform operations must supply the certificate authority-issued material.

The reservation-expiry worker emits `inventory-released` with reason `RESERVATION_EXPIRED`. The reconciliation worker emits `low-inventory` or `out-of-stock` for active products at or under `INVENTORY_LOW_STOCK_THRESHOLD` (default 5). Notification consumers own seller/admin delivery retries and their DLQs; no customer availability data is emitted.

All Inventory-originated events are written to `inventory_event_outbox` in the same database transaction as the associated inventory state transition. The publisher claims a row with a 60-second lease, publishes it to Kafka, and marks it published only after broker acknowledgement. Failures use exponential backoff and become `DEAD` after ten attempts. Alert on `inventory_outbox_dead_total`; rows can be inspected/replayed by operations after correcting the underlying broker issue.

Alert on `inventory_grpc_authorization_failures_total`, expiry release failures, lifecycle DLQ/lag, reconciliation run failures, and low/out-of-stock event rates. Product Service ownership timeouts map to retryable `PRODUCT_SERVICE_UNAVAILABLE`; callers should retry with bounded backoff.
