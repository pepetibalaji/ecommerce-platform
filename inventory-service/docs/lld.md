# Inventory Service low-level design

## Components

| Component | Role |
| --- | --- |
| `InventoryService` | Transactional reservation, adjustment, and seller ownership operations. |
| `InventoryGrpcService` | Required-ID internal reserve/release/deduct/read contract. |
| `ReservationExpiryWorker` | Releases expired `RESERVED` rows. |
| `InventoryReconciliationWorker` | Reconciles Product snapshots and emits rate-limited availability events. |
| `InventoryOutboxService` / publisher | Persists, leases, retries, and publishes Kafka events. |
| Lifecycle consumers | Apply ordered Product Kafka snapshots with retry/DLQ. |

## Concurrency and transitions

Each stock mutation uses a PostgreSQL pessimistic lock on the inventory row, then locks the reservation row when present. This serializes mutations for one product.

```text
RESERVED → RELEASED
RESERVED → DEDUCTED
RELEASED/DEDUCTED → any other state: rejected
```

Reserve applies `available -= quantity; reserved += quantity`; release reverses it; deduct applies `reserved -= quantity`. Database checks prevent negative persisted counters.

## Outbox

The expiry-release transaction also inserts its event into `inventory_event_outbox`. A scheduled publisher claims a row with a 60-second lease, waits for Kafka acknowledgement, and marks it `PUBLISHED`; failures back off and become `DEAD` after ten attempts.

## Product reconciliation

Inventory pages `ProductSnapshotService/ListInventorySnapshots`, upserts product/seller/active/version metadata, preserves counters, and deactivates rows absent from a completed authoritative snapshot.
