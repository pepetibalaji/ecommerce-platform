# Functional Requirements

## Implemented capabilities

| Area | Current behaviour |
| --- | --- |
| Identity | Customers register, log in, refresh/logout, manage their profile, and administrators manage users. |
| Catalog | Public product browsing and administrator product management. |
| Cart | Authenticated customer cart stored in Redis. |
| Inventory | Stock administration and idempotent reserve/release operations through gRPC. |
| Orders | Customers create orders; Order Service reserves inventory and manages order state. |
| Payments | Payment creation, checkout session, signed webhook handling, payment outcomes, and refunds. |
| Notifications | Kafka-driven transactional email intents, provider delivery attempts, retries, preferences, and failed-delivery visibility. |
| Recipient directory | Auth registration/deactivation events maintain Notification Service's local email directory. |

## Required business flows

### Order and payment

1. Customer creates an order.
2. Order Service reserves inventory and publishes `order-created`.
3. Payment Service creates a pending payment.
4. Provider webhook decides payment success or failure.
5. Payment Service publishes the outcome.
6. Order Service confirms/fails the order and releases stock when required.

### Transactional notifications

1. Auth publishes `user-contact-updated` when a customer registers or is deactivated.
2. Notification Service stores the recipient locally.
3. A supported domain event creates one notification per event/recipient/channel.
4. The worker sends email asynchronously and records the provider result.
5. Provider failure retries without affecting the originating business workflow.

## Notification event status

| Event | Current state |
| --- | --- |
| `order-created`, `payment-success`, `payment-failed`, `order-cancelled`, `payment-refund-completed` | Notification handling implemented; delivery depends on an active recipient directory entry. |
| `order-shipped`, `order-delivered` | Consumer ready; Fulfilment producer is still required. |
| `low-inventory` | Deferred. |
| `seller-order-paid` | Consumer ready; seller ownership enrichment is still required. |

## Remaining functional work

- Backfill `notification_recipients` for users created before recipient events were introduced.
- Add Fulfilment Service and shipment events.
- Add inventory low-stock producer and seller ownership event data.
- Add SMS, push, inbox, and approved marketing flows only after policy/design approval.
