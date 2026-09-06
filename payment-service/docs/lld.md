# Payment Service low-level design

## Components

| Component | Role |
| --- | --- |
| Order-created consumer | Calls `preparePaymentFromOrder`; unique order/idempotency keys make it safe for duplicate delivery. |
| Checkout service | Enforces owner/state, reuses active attempt, or calls selected gateway and persists an attempt. |
| Webhook service | Verifies/parses provider event, inserts unique provider-event record, resolves attempt/refund, changes state, publishes outcome. |
| Refund service | Validates amount/currency/idempotency and calls gateway. |
| Query/controllers | Separate customer-owned, public, webhook, and admin contracts. |

`Payment` has JPA `@Version` optimistic locking. Checkout creates an attempt, then marks payment `REQUIRES_CUSTOMER_ACTION`. Webhook records use `(provider, provider_event_id)` uniqueness; duplicate insert is treated as idempotent. Refunds use their own idempotency key, provider refund ID uniqueness, and aggregate amount validation.

Provider event publication is invoked during webhook processing. A durable event outbox is not present, so database update and Kafka outcome delivery are not atomic.
