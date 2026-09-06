# Inventory Service high-level design

## Responsibility and boundary

Inventory Service owns current stock counters and the lifecycle of stock reservations. `availableStock` is sellable stock; `reservedStock` is stock held for an order. It is the only service permitted to mutate these values.

It does not own product catalog content/ownership (Product Service), orders/payment lifecycle (Order/Payment Service), fulfillment, or customer cart state.

```text
Product Service -- product-created --> Kafka --> Inventory Service --> PostgreSQL
Order Service ---- gRPC reserve/release/deduct --> Inventory Service --> PostgreSQL
Seller/admin ----- REST + JWT -------------> Inventory Service
                                      |
                                      +--> Product Service public product read (seller verification)
```

## Main flows

### Product provisioning

Product-created events create one zero-stock row per unique product. Repeated deliveries return the existing row, so at-least-once Kafka delivery is safe for the inventory row.

### Reservation lifecycle

1. Order Service calls `ReserveStock` with stable product, quantity, and reservation UUID.
2. A transaction takes a PostgreSQL pessimistic write lock on the inventory row.
3. Available stock decreases and reserved stock increases; a `RESERVED` ledger row is created.
4. Cancellation/failure calls `ReleaseStock`, moving reserved back to available and status to `RELEASED`.
5. Fulfillment calls `DeductStock`, reducing reserved only and status to `DEDUCTED`.

Stable IDs make retried commands idempotent for their terminal/active state. The old ID-less RPC variants remain only for rolling deployment compatibility.

## Security and ownership

Admin REST has global access. Seller REST checks the seller's JWT `userId` against Product Service's public product response before access; a mismatch becomes not-found. An ADMIN caller bypasses that remote ownership check. gRPC security is not implemented in this module and must be protected at network/service-boundary level.
