# Order Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Dashed arrows carry asynchronous events.

```mermaid
flowchart TD
    G["Gateway"] --> C

    subgraph ORDER["Order service"]
        C["Order controllers"] --> S["OrderServiceImpl<br/>checkout + lifecycle"]
        S --> DB[("PostgreSQL<br/>orders, item snapshots<br/>idempotency + processed events")]
        S --> OB[("Order-created / refund-command outboxes")]
        OB --> PW["Kafka publishing workers"]
        S --> RB[("Inventory-release / compensation outboxes")]
        RB --> RW["Reservation release workers"]
        PC["PaymentOutcomeConsumer"] --> S
        S --> AC["After-commit publisher"]
    end

    S -->|"HTTP: current product data"| P["Product service"]
    S -->|"gRPC: check / reserve stock"| I["Inventory service"]
    RW -->|"gRPC: release reservation"| I
    PW -.-> K["Kafka<br/>order-created / refund / cancellation requests"]
    K -.-> PAY["Payment service"]
    PAY -.-> KO["Kafka<br/>payment outcomes"]
    KO -.-> PC
    AC -.-> KC["Kafka: order-completed"]
    KC -.-> CART["Cart service"]
    KC -.-> N["Notification service"]
```

The outboxes are PostgreSQL tables. Order-created and refund/cancellation commands use durable publishing workers. Inventory release and checkout compensation workers call Inventory directly through gRPC.

In the current implementation, `order-completed` uses an after-commit Kafka send rather than a durable outbox. It is emitted when payment success moves a pending order to confirmed, and triggers cart cleanup and confirmation email.

Source: [OrderServiceImpl](../src/main/java/com/ecommerce/order/service/OrderServiceImpl.java), [OrderEventPublisher](../src/main/java/com/ecommerce/order/kafka/OrderEventPublisher.java), [PaymentOutcomeConsumer](../src/main/java/com/ecommerce/order/kafka/PaymentOutcomeConsumer.java).

See [high-level design](hld.md) and [events and operations](events-and-operations.md) for lifecycle and worker details.
