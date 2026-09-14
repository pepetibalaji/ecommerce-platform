# Order Service high-level design

## Ownership and dependencies

Order Service owns the purchase record and its immutable item, price, and shipping snapshots. At checkout it validates current catalogue data, reserves Inventory stock, creates payment-initiation work, and owns the order-facing payment outcome state. It does not own Product data, Inventory counters, payment execution, shipments, fulfilment, or saved-address authority.

```text
Customer -> Gateway -> Order Service -> Product Service (current eligibility/snapshot)
                                  -> Inventory gRPC (availability/reserve/release)
                                  -> PostgreSQL orders + idempotency + durable outboxes
                                  -> Kafka order-created -----------------> Payment Service

Payment outcomes ------------------------------------------------> Order Service
Order outbox workers -------------------------------------------> Kafka / Inventory
Seller/Admin ---------------------------------------------------> scoped lifecycle reads
```

There is no Address Service. The customer provides a full shipping snapshot at checkout; Order Service retains it for the purchase record and does not expose internal reservation IDs to browsers.

## Major flows

### Idempotent checkout

1. Gateway authenticates the customer; Order Service requires an `Idempotency-Key`.
2. Order Service claims the customer/key/request hash using a database-backed idempotency record.
3. It resolves every requested Product, checks Inventory availability, and reserves each line using a generated reservation ID.
4. In one database transaction it saves the `PENDING` order, immutable snapshots, completed idempotency record, and `order_created_outbox` row.
5. A leased worker publishes `order-created` asynchronously using the order ID as Kafka key. Payment Service creates payment state idempotently.
6. If any reservation was attempted but checkout cannot complete, a separate durable compensation transaction records release work using the original reservation IDs.

### Payment, expiry, and inventory release

```text
PENDING
  ├─ payment success ───────────────> CONFIRMED
  ├─ payment failure ───────────────> PAYMENT_FAILED + durable release
  ├─ expiry worker ─────────────────> PAYMENT_EXPIRED + durable release
  └─ customer cancellation ─────────> CANCELLED + durable release

CONFIRMED
  └─ customer cancellation/admin request -> REFUND_REQUESTED + durable refund command

REFUND_REQUESTED
  ├─ full refund completed ─────────> REFUNDED + release when safe
  └─ rejected/partial/unsafe outcome -> REFUND_REQUIRES_FULFILMENT_REVIEW
```

Payment event IDs are stored in an inbox. A duplicate event is ignored, and a late event cannot revive an expired or cancelled order. Inventory release commands are idempotent by reservation ID; already-deducted stock becomes a fulfilment/manual-review case instead of a blind release.

### Operations and audit

Order-created, checkout-compensation, inventory-release, and refund-request hand-offs are durable rows with retries and terminal states. Administrators can read outbox reconciliation counts and immutable lifecycle audit history. Recovery is deliberate: a terminal failure or `MANUAL_REVIEW` row must be investigated rather than silently discarded or automatically overridden.
