# Notification Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Dashed arrows carry asynchronous events.

```mermaid
flowchart TD
    K["Kafka<br/>Auth, order, payment and stock events"] -.-> C

    subgraph NOTIFICATION["Notification service"]
        C["NotificationKafkaConsumer"] --> E["Event handlers<br/>deduplicate + create notification"]
        C --> RD["Recipient directory handler"]
        E --> DB[("PostgreSQL<br/>pending notifications, processed events<br/>recipients + delivery history")]
        RD --> DB
        DB --> W["Scheduled delivery worker<br/>recipient lookup + retries"]
        W --> AD["Email provider adapter"]
        W -->|"Record sent / failed / retry state"| DB
    end

    W -->|"HTTP: obtain action token for Auth emails"| A["Auth service"]
    AD --> EP["Configured email provider<br/>SMTP / Mailtrap / Resend / logging"]
    EP --> U["Recipient or development log"]
```

Kafka handling persists notification work before scheduled delivery. Auth action tokens are obtained immediately before sending and are not retained in notification payload storage. Order confirmation email comes from `order-completed`, after Order confirms payment. The selected provider controls whether delivery sends email or writes a development log.

Source: [NotificationKafkaConsumer](../src/main/java/com/ecommerce/notification/kafka/NotificationKafkaConsumer.java), [NotificationDeliveryService](../src/main/java/com/ecommerce/notification/service/NotificationDeliveryService.java).

See [high-level design](hld.md) and [events and operations](events-and-operations.md) for topics and retry behavior.
