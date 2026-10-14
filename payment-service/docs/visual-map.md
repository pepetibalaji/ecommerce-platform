# Payment Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Dashed arrows carry asynchronous events.

```mermaid
flowchart TD
    K["Kafka<br/>order-created / cancellation / refund requests"] -.-> C
    G["Gateway<br/>checkout requests"] --> CS

    subgraph PAYMENT["Payment service"]
        C["Kafka consumers"] --> S["Payment / cancellation / refund logic"]
        CS["Checkout controller + service"] --> S
        S --> DB[("PostgreSQL<br/>payments, attempts, refunds")]
        WH["Webhook verification<br/>durable webhook inbox"] --> S
        S --> O[("Payment outbox<br/>committed with state changes")]
        O --> W["Outbox worker<br/>leases + retries"]
    end

    S -->|"HTTP: verify trusted order snapshot"| OS["Order service"]
    S -->|"Create checkout / execute refund"| PR["Payment provider<br/>Stripe / development sandbox"]
    PR -->|"Signed webhook"| WH
    W -.-> OUT["Kafka<br/>payment + refund outcomes"]
    OUT -.-> OS
    OUT -.->|"Failure / refund-completed events"| N["Notification service"]
```

Payment validates the trusted Order snapshot before preparation. Verified provider webhooks drive payment confirmation; browser redirects only read state. Payment/refund changes and their outbox events commit together in PostgreSQL. Provider selection and enablement depend on deployment configuration.

Source: [TrustedOrderClient](../src/main/java/com/ecommerce/payment/order/TrustedOrderClient.java), [KafkaPaymentEventPublisher](../src/main/java/com/ecommerce/payment/kafka/producer/KafkaPaymentEventPublisher.java), [PaymentOutboxWorker](../src/main/java/com/ecommerce/payment/outbox/PaymentOutboxWorker.java).

See [high-level design](hld.md) and [production reliability](production-reliability.md) for provider and recovery details.
