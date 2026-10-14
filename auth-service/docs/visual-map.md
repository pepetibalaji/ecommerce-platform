# Auth Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Dashed arrows carry asynchronous events.

```mermaid
flowchart TD
    G["Gateway"] --> C

    subgraph AUTH["Auth service"]
        C["Controllers"] --> S["Registration / login / sessions<br/>verification / password reset"]
        S --> DB[("PostgreSQL<br/>users, sessions, action tokens, audit")]
        S --> O[("PostgreSQL outbox<br/>saved with business changes")]
        O --> W["Scheduled outbox publisher<br/>retry on failure"]
        S --> R[("Redis<br/>token blacklist + rate limits")]
        J["JWKS endpoint<br/>public signing keys"]
        T["Internal delivery-token endpoint"]
    end

    W -.-> K["Kafka<br/>Auth action + contact events"]
    K -.-> N["Notification service"]
    N -->|"Request email action token"| T
    V["Gateway / business services"] -->|"Fetch JWT verification keys"| J
```

Redis is a separate dependency for revocation and abuse protection. Email events flow through the database outbox, publisher, Kafka, and Notification. Notification obtains usable action tokens through the protected internal endpoint immediately before delivery.

Source: [AuthOutboxPublisher](../src/main/java/com/ecommerce/auth/messaging/AuthOutboxPublisher.java), [AuthAbuseProtection](../src/main/java/com/ecommerce/auth/service/AuthAbuseProtection.java), [TokenBlacklistService](../src/main/java/com/ecommerce/auth/service/TokenBlacklistService.java).

See [high-level design](hld.md) and [events and operations](events-and-operations.md) for contracts and recovery details.
