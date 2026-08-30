# Notification Service

Notification Service is a separately deployed Kafka consumer with its own `notification_db`. It creates an intent in PostgreSQL before attempting an email provider call, so provider availability cannot affect order, payment, inventory, or fulfilment processing.

## Supported topics and recipient fields

| Topic | Type | Recipient field |
| --- | --- | --- |
| `order-created` | `ORDER_RECEIVED` | `userId` |
| `payment-success` | `PAYMENT_SUCCESSFUL` | `userId` |
| `payment-failed` | `PAYMENT_FAILED` | `userId` |
| `order-cancelled` | `ORDER_CANCELLED` | `userId` |
| `payment-refund-completed` | `REFUND_PROCESSED` | Requires `userId` (currently absent from the shared refund contract) |
| `order-shipped`, `order-delivered` | Shipment notices | `userId` |
| `low-inventory` | `LOW_STOCK_WARNING` | `sellerUserId` or `adminUserId` |
| `seller-order-paid` | `SELLER_NEW_ORDER` | `userId` |
| `user-contact-updated` | Recipient directory update | `userId`, `email`, `active` |

The current Order and Payment publishers provide the first four recipient fields. Fulfilment, inventory, seller-order and refund publishers must be added/enriched before those notifications can be delivered. The `user-contact-updated` topic is the only approved event carrying an email address; it contains no credentials, card data, or other profile data.

## Delivery and operations

`notifications` is unique by `(event_id, recipient_user_id, channel)` and `notification_processed_events` suppresses duplicate Kafka consumption. A `PENDING` record is committed first. The worker records every provider attempt in `notification_deliveries`, applies capped exponential backoff with jitter, and marks exhausted notifications `FAILED`. Prometheus metric: `notification_delivery_exhausted_total`.

Investigate failures through `GET /api/v1/notifications/admin/failed` and `GET /api/v1/notifications/admin/{notificationId}/deliveries`; alert on a non-zero increase of the exhaustion metric. The local logging adapter is deliberately non-production. Production needs an `EmailProvider` adapter. Recipient email is read from the Notification Service's local `notification_recipients` directory, so delivery does not call Auth Service.

## External configuration repository changes

Create `dev/notification-service-dev.yml` (and stage/prod equivalents) in the separate configuration repository. At minimum configure the database, Kafka string consumer, and delivery settings. OAuth2 issuer/JWK settings are only required to protect the Notification Service HTTP APIs; they are not used to resolve recipient emails.

```yaml
notification:
  kafka:
    consumer-group: notification-service
  max-attempts: 5
  retry-base-delay: 30s
  delivery-poll-ms: 5000
  provider: logging-email
  recipient-email-overrides: {} # local-only test override; normal delivery uses notification_recipients
```

Keep provider credentials exclusively in the configuration secret store/environment, never in this repository or Kafka events.
