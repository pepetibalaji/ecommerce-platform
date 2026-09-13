# Inventory Service high-level design

Inventory is the authoritative service for sellable stock and checkout reservations. Customer applications never call it directly; Order Service is the checkout boundary.

```text
Frontend → Order Service → Inventory gRPC → PostgreSQL
                       ↘ Payment / fulfilment outcomes
Product Service → Kafka lifecycle events → Inventory
Product Service ← ProductSnapshot gRPC ← Inventory reconciliation
Seller/Admin → JWT REST → Inventory
Inventory outbox → Kafka → Notification Service
```

## Responsibilities

* Maintain `availableStock` and `reservedStock` without negative values.
* Reserve, release, and deduct a stable order-line reservation exactly once.
* Expire abandoned reservations and preserve an audit trail.
* Synchronize product seller and active/archived state without overwriting stock.
* Publish durable operational events through the transactional outbox.

## Security boundary

REST management endpoints require JWT roles. Seller actions are verified against Product Service ownership. Inventory gRPC accepts only allow-listed internal callers and production deployments enable mTLS. Product snapshot gRPC is Inventory-only and follows the same deployment controls.

## Checkout lifecycle

`RESERVED` decreases available/increases reserved. It can become `DEDUCTED` after payment/fulfilment, or `RELEASED` after cancellation, failure, or expiry. Repeated commands with the same reservation ID are no-ops only for their completed state; conflicting transitions are rejected.
