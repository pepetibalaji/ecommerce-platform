# Low-Level Design

## Event contracts

| Topic | Producer | Consumer | Key | Purpose |
| --- | --- | --- | --- | --- |
| `order-created` | Order | Payment, Notification | `orderId` | Prepare payment and create order-received notification. |
| `payment-success` | Payment | Order, Notification | `orderId` | Confirm order and notify customer. |
| `payment-failed` | Payment | Order, Notification | `orderId` | Fail order and notify customer. |
| `payment-expired` | Payment | Order | `orderId` | Expire the Order and queue eligible inventory release. |
| `payment-cancellation-requested` | Order | Payment | `orderId` | Durably resolve unpaid cancellation or expiry; orchestrate a refund if payment races with cancellation. |
| `payment-refund-requested` | Order | Payment | `orderId` | Reserve and execute durable refund work. |
| `payment-refund-request-rejected` | Payment | Order | `orderId` | Report a business refusal for refund review. |
| `payment-refund-completed` | Payment | Order, Notification | `orderId` | Apply partial/full refund lifecycle and notify refund completion. |
| `payment-refund-failed` | Payment | Order | `orderId` | Surface refund failure or uncertain acceptance for review. |
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
