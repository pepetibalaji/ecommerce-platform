# Order Service high-level design

## Ownership and dependencies

Order Service owns a purchase record, not a mutable cart. At checkout it creates immutable line-item name/price/seller snapshots, captures shipping data, reserves stock, and starts payment processing through the `order-created` event. It owns payment outcome state and release compensation work.

```text
Customer -> Gateway -> Order Service -> Product Service (current valid catalog)
                                  -> Inventory gRPC (availability + reservation)
                                  -> PostgreSQL orders / inbox / release outbox
                                  -> Kafka order-created

Payment Kafka outcomes ----------> Order Service -> state update / release outbox
Release worker -------------------> Inventory gRPC ReleaseStock
Seller ---------------------------> filtered order-item view
```

Order Service does not own catalog data, inventory counters, payment execution, fulfillment, or customer address authority. It accepts a shipping snapshot directly; no Address Service validation exists.

## Major flows

### Checkout

1. Validate request and JWT customer.
2. Resolve every product from Product Service and snapshot name, seller, and current price.
3. Check inventory and reserve each line with a unique reservation ID.
4. Persist PENDING order and items in PostgreSQL.
5. Asynchronously publish `order-created` to Kafka.
6. If checkout fails before return, best-effort synchronous releases are attempted for every reservation call started.

### Payment and release

Payment success confirms a PENDING order. Payment failure and eligible refunds enqueue idempotent inventory-release rows and update order state. A scheduled worker calls Inventory release with the original reservation ID until completed, terminally failed, or requiring manual fulfillment review. Payment event IDs are recorded to make consumption idempotent.
