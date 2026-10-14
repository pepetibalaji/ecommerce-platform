# Inventory Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Dashed arrows carry asynchronous events.

```mermaid
flowchart TD
    OS["Order service"] -->|"gRPC: check / reserve / release"| GR
    G["Gateway<br/>seller / admin requests"] --> RC
    K["Kafka: product.lifecycle.v1"] -.-> LC

    subgraph INVENTORY["Inventory service"]
        GR["InventoryGrpcService"] --> S["InventoryService<br/>stock + reservations"]
        RC["Inventory REST controllers"] --> S
        LC["ProductLifecycleConsumer"] --> DB[("PostgreSQL<br/>stock, reservations, product metadata")]
        S --> DB
        EX["Reservation expiry worker"] --> S
        SY["Product reconciliation worker"] --> DB
        S --> O[("Inventory event outbox")]
        O --> W["Outbox publisher"]
    end

    S -->|"HTTP: seller ownership checks"| P["Product service"]
    SY -->|"gRPC: product snapshots"| P
    W -.-> OUT["Kafka<br/>low-inventory / out-of-stock<br/>inventory-released"]
    OUT -.->|"Stock warning topics"| N["Notification service"]
```

Product lifecycle events and reconciliation update product metadata while preserving stock counters. Reservation expiry and stock warnings use the PostgreSQL event outbox. Notification consumes the stock warning topics. The legacy `product-created` consumer remains for rolling-upgrade compatibility; the map shows the primary lifecycle path.

Source: [InventoryService](../src/main/java/com/ecommerce/inventory/service/InventoryService.java), [InventoryEventPublisher](../src/main/java/com/ecommerce/inventory/service/InventoryEventPublisher.java), [InventoryReconciliationWorker](../src/main/java/com/ecommerce/inventory/service/InventoryReconciliationWorker.java).

See [high-level design](hld.md) and [events and operations](events-and-operations.md) for reservation and recovery details.
