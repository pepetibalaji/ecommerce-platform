# Payment Service data model

Flyway V1/V2 create PostgreSQL payment storage.

| Table | Key fields | Purpose |
| --- | --- | --- |
| `payments` | unique order ID, unique idempotency key, user, amount `NUMERIC(19,2)`, currency, status/provider, correlation/trace IDs, version | One payment per order. |
| `payment_attempts` | payment FK, provider session/intent/charge identifiers, checkout URL, status, expiry | Provider checkout attempts; provider identifiers are uniquely indexed. |
| `payment_refunds` | payment FK, amount/currency, provider refund ID, status, idempotency key | Refund lifecycle; idempotency and provider refund ID are unique. |
| `payment_webhook_events` | provider + provider event ID unique, optional payment FK, event type, processing status | Webhook inbox/idempotency audit. |

Payment indexes support user, status, provider, creation, correlation, and trace queries. Attempt/refund/webhook tables have payment/status/time indexes. FK deletion cascades attempts/refunds; webhook payment reference becomes null on payment deletion.
