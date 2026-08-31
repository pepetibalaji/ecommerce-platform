# High-Level Design

## Architecture

```text
Client -> API Gateway -> business services

Order Service -- gRPC --> Inventory Service
Order Service -- Kafka --> Payment Service
Payment Service -- Kafka --> Order Service + Notification Service
Auth Service -- Kafka --> Notification Service
Notification Service -- SMTP/API --> Mailtrap or email provider
```

Config Server supplies environment configuration from the separate configuration repository. PostgreSQL is used by Auth, Inventory, Order, Payment, and Notification; Product uses MongoDB; Cart and token-blacklist state use Redis.

## Service boundaries

| Service | Owns | Primary responsibility |
| --- | --- | --- |
| Gateway | No business data | Public routing and JWT validation. |
| Auth | Users, refresh tokens, blacklist | Identity and user lifecycle. |
| Product | Catalog documents | Product browsing and administration. |
| Inventory | Stock and reservations | Reserve/release stock. |
| Cart | Redis cart state | Customer cart lifecycle. |
| Order | Orders and inventory-release outbox | Order state and compensation. |
| Payment | Payments, attempts, refunds, webhooks | Provider-backed payment processing. |
| Notification | Notifications, delivery attempts, recipients | Transactional email delivery. |

## Design principles

- REST is used for external requests, gRPC for inventory commands, and Kafka for asynchronous business events.
- A provider outage must not fail an order or payment transaction.
- Recipient email is maintained locally by Notification Service; delivery does not call Auth Service.
- Future services publish their own domain events; Notification Service only consumes them.
