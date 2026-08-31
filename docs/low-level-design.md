# Low-Level Design

## Event contracts

| Topic | Producer | Consumer | Key | Purpose |
| --- | --- | --- | --- | --- |
| `order-created` | Order | Payment, Notification | `orderId` | Prepare payment and create order-received notification. |
| `payment-success` | Payment | Order, Notification | `orderId` | Confirm order and notify customer. |
| `payment-failed` | Payment | Order, Notification | `orderId` | Fail order and notify customer. |
| `payment-refund-completed` | Payment | Notification | `orderId` | Notify refund completion. |
| `user-contact-updated` | Auth | Notification | `userId` | Upsert/deactivate local email recipient. |

Events carry an `eventId`. Consumers store processed IDs to make at-least-once Kafka delivery safe.

## Notification processing

```text
Kafka record
  -> validate and deduplicate eventId
  -> insert notification as PENDING
  -> delivery worker finds active local recipient
  -> record provider attempt
  -> SENT, retry with backoff, or terminal FAILED
```

`notifications` represents the business intent. `notification_deliveries` records provider attempts. `notification_recipients` stores only user ID, email, active flag, and timestamp.

## Security and configuration

- Config Server loads service configuration from `ecommerce-config-repo`.
- Development secrets are in ignored local environment files; stage/prod uses a secret manager.
- Notification Service uses Mailtrap Sandbox in development and Mailtrap Transactional Email in stage after domain verification.

## Operations

- Check `/actuator/health`, Prometheus, Kafka lag, structured logs, and failed-delivery APIs first.
- Do not resend a notification until the provider/configuration/recipient cause is understood.
- Replaying an event with the same ID is safe only where consumer idempotency is intended; create a new event only under approved business policy.
