# Inventory Service production contract

Inventory owns sellable and reserved stock. Customer applications never call it directly; Order Service is the checkout-facing dependency.

## Reservation lifecycle

All gRPC mutations (`ReserveStock`, `ReleaseStock`, `DeductStock`) require a UUID `reservationId`. Order Service creates one stable ID per order line and reuses it on retries. Quantity-only mutation behavior has been removed.

`RESERVED` rows expire after `inventory.reservation.ttl` (15 minutes by default). The expiry worker releases only expired `RESERVED` rows under database locks; released and deducted rows are never changed by the worker. Reserve/release/deduct are idempotent for the matching reservation state and reject mismatched product or quantity requests.

Reservation timestamps and expiry use UTC `Instant`/`TIMESTAMP WITH TIME ZONE`. `inventory_reservation_audit` is append-only transition history for support and reconciliation.

## Stock administration

The former replace-available-stock endpoint is removed. Use `POST /api/v1/admin/inventory/{productId}/adjustments` (or the seller equivalent) with a non-zero `adjustment`, a reason (`STOCK_RECEIVED`, `STOCK_CORRECTION`, `DAMAGE`, `RETURN`, `MANUAL_RECONCILIATION`), and optional `referenceId`.

Database checks prevent negative available/reserved counters. Each accepted adjustment writes an immutable `inventory_stock_ledger` record with before/after counters, seller, actor, reason, reference, and UTC timestamp. Adjustments cannot consume stock currently reserved for checkout.

## Internal API security and operations

Inventory gRPC accepts only allow-listed callers (`x-internal-caller`, default `order-service`). Production deployments must set `INVENTORY_GRPC_REQUIRE_MTLS=true`, terminate no public gRPC routes, and provide certificate/network-policy configuration through the deployment platform. Authorization denials increment `inventory_grpc_authorization_failures_total`; expiry releases increment `inventory_reservations_expired_released_total`.

Product lifecycle snapshots provision inventory atomically and prevent new reservations for deactivated or archived products. Product ownership verification remains a retryable Product Service dependency; callers must retry transient dependency failures rather than treating them as seller authorization failures.

## Required production monitoring

Alert on authorization failures, expired-release failures, reservation/stock invariant reconciliation discrepancies, lifecycle consumer DLQ/lag, and low/out-of-stock policy events. Notification ownership belongs to seller/admin workflows; raw counter values must not be exposed to customers.
