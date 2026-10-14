# Notification Service

## What this service is

Notification Service runs on port `8088` and sends transactional customer/seller email asynchronously. It consumes business and identity Kafka events, stores notification state in PostgreSQL, and delivers through the configured provider without blocking business workflows.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- Spring Kafka consumer
- Spring Mail SMTP, Mailtrap, and Resend email adapters
- Spring Security OAuth2 Resource Server, Actuator, Prometheus

## Data owned

- `notifications`: one delivery intent and overall status.
- `notification_deliveries`: each provider attempt and error/reference.
- `notification_preferences`: user/channel/type preference state.
- `notification_processed_events`: Kafka idempotency.
- `notification_recipients`: local user ID, email, active state.

## End-to-end flow

```text
User registration
  -> Auth publishes user-contact-updated
  -> Notification Service upserts local recipient email

Confirmed order/payment event
  -> Notification Kafka consumer validates eventId
  -> insert one PENDING notification
  -> delivery worker reads active recipient locally
  -> send email through the configured provider
  -> store provider message ID as SENT
  -> on failure, record attempt and retry with backoff + jitter
  -> after max attempts, mark FAILED and raise metric/alert
```

Supported now: order confirmation after payment, payment failure, cancellation,
and refund notification handling. Shipment, low inventory, and seller paid-order
notifications wait for their upstream event producers. `order-created` only
starts the payment flow; it does not send a customer confirmation. Notification
Service sends one confirmation from `order-completed`, which Order Service emits
after the order becomes `CONFIRMED`. Queued confirmations from the earlier
`order-created` and `payment-success` flow are skipped on delivery. A
`payment-failed` event with `failureCode=PAYMENT_CANCELLED` uses the order
cancellation email rather than a payment failure email.

## Run locally

```bash
cd notification-service
mvn spring-boot:run
```

Requires PostgreSQL, Kafka, Config Server, and credentials for the selected email provider.

## Email provider by environment

Choose the provider with `NOTIFICATION_EMAIL_PROVIDER`. The delivery worker,
event handling, and retry behavior do not change when the provider changes.

| Value | Use | Required settings |
| --- | --- | --- |
| `smtp` | Local Mailpit or another SMTP server | `NOTIFICATION_SMTP_HOST`, `NOTIFICATION_SMTP_PORT`, `NOTIFICATION_SMTP_FROM_EMAIL` |
| `resend` | Real delivery through Resend | `RESEND_API_KEY`, `RESEND_FROM_EMAIL`, optional `RESEND_TEMPLATE_*_ID` |
| `mailtrap-sandbox` | Existing Mailtrap test inbox | Mailtrap Sandbox SMTP and API settings |
| `mailtrap` | Real delivery through Mailtrap Email API | `MAILTRAP_API_TOKEN`, `MAILTRAP_FROM_EMAIL` |

For local development, start Mailpit with `docker compose up -d mailpit`, set
`NOTIFICATION_EMAIL_PROVIDER=smtp` in `ecommerce-config-repo/dev/.env`, and
restart Notification Service. Its SMTP host is `localhost:1025` when the app
runs on your machine; use `mailpit:1025` if the app runs inside this Compose
network. Open `http://localhost:8025` to inspect every captured message.
Mailpit never delivers these messages to real recipients. The SMTP provider
sends both text and event-specific Pepekart HTML, including account action
buttons and order, payment, refund, or stock details when the event supplies
them. The registration email's HTML source can be imported into Resend from
[`templates/pepekart-email-verification.html`](templates/pepekart-email-verification.html).
Hosted Resend and Mailtrap templates are managed in those providers; their
appearance changes only after you import or edit and publish them there.
For an authenticated SMTP service, also set `NOTIFICATION_SMTP_USERNAME`,
`NOTIFICATION_SMTP_PASSWORD`, `NOTIFICATION_SMTP_AUTH=true`, and
`NOTIFICATION_SMTP_STARTTLS=true` where required by that service.

For stage or production, set `NOTIFICATION_EMAIL_PROVIDER=resend` and the
Resend values below in that environment's secrets, then restart that service.
Use a public `AUTH_EMAIL_VERIFICATION_URL` for links sent to real users.

## Resend

Set these values in the selected environment's secret configuration and restart
Notification Service after creating a Resend API key and verifying your sender
domain. Resend delivers to real inboxes; it is not a Mailtrap-style sandbox.

```properties
NOTIFICATION_EMAIL_PROVIDER=resend
RESEND_API_KEY=your-resend-api-key
RESEND_FROM_EMAIL=hello@your-verified-domain.example
RESEND_FROM_NAME=Pepekart
RESEND_TEMPLATE_EMAIL_VERIFICATION_ID=your-published-resend-template-id
```

Create and publish a verification template in Resend with a `verificationUrl`
variable for the confirmation button's link. Each registration supplies that
user's generated URL. Resend template IDs or aliases belong to Resend; the
Mailtrap UUID cannot be reused. For the other notification types, configure
`RESEND_TEMPLATE_<NOTIFICATION_TYPE>_ID`, such as
`RESEND_TEMPLATE_PASSWORD_RESET_ID` or `RESEND_TEMPLATE_ORDER_RECEIVED_ID`.
An importable verification email is in
[`templates/pepekart-email-verification.html`](templates/pepekart-email-verification.html).
Set its Resend subject to `Verify your Pepekart email` and publish it.
Template variables are listed in the table below. Types without a configured
Resend template ID send the existing plain-text content through Resend. Resend
must accept the template request before the notification is marked `SENT`.

For verification links used outside the development computer, also set
`AUTH_EMAIL_VERIFICATION_URL` to the public frontend URL instead of `localhost`.
Keep the Resend API key in server-side config only. Do not commit it.

## Mailtrap templates

Notification Service selects a Mailtrap template by notification type. Create each
template in Mailtrap, then set its UUID in the environment or config file using
the keys below. The existing verification template UUID defaults to
`31a79ec0-e191-49d6-a730-4a50b30da69d`; sandbox inbox ID defaults to `4883696`.
Override them with `MAILTRAP_TEMPLATE_EMAIL_VERIFICATION_UUID` and
`MAILTRAP_SANDBOX_INBOX_ID` as needed. The old
`MAILTRAP_VERIFICATION_TEMPLATE_UUID` key also remains supported.

For local Sandbox testing, add `MAILTRAP_API_TOKEN` to the ignored
`ecommerce-config-repo/dev/.env` using the token from the template's Integration >
Email Testing panel. The `mailtrap-sandbox` provider sends configured templates
through the Sandbox API. If the API token or an event's template UUID is absent,
that event keeps its plain-text SMTP delivery. The `mailtrap` transactional provider
also uses a configured template UUID; events without one keep their text email.
Do not commit the API token. Sandbox emails remain in Mailtrap and do not reach
real recipient inboxes.

If Mailtrap Sandbox rejects the template API token with HTTP 401 or 403, the
provider uses plain-text SMTP for the current and subsequent messages until
Notification Service restarts. This keeps registration links available while the
Sandbox API token is corrected. Replace the token with the one shown for the
selected sandbox in Mailtrap's Email Testing integration panel, then restart.

| Notification type | UUID environment key | Template variables |
| --- | --- | --- |
| `EMAIL_VERIFICATION` | `MAILTRAP_TEMPLATE_EMAIL_VERIFICATION_UUID` | `verificationUrl` |
| `PASSWORD_RESET` | `MAILTRAP_TEMPLATE_PASSWORD_RESET_UUID` | `resetUrl` |
| `EMAIL_CHANGE_CONFIRMATION` | `MAILTRAP_TEMPLATE_EMAIL_CHANGE_CONFIRMATION_UUID` | `confirmationUrl` |
| `ORDER_RECEIVED` (confirmed order) | `MAILTRAP_TEMPLATE_ORDER_RECEIVED_UUID` | `orderId`, `paymentId`, `amount`, `currency` |
| `PAYMENT_SUCCESSFUL` (legacy, no new emails) | `MAILTRAP_TEMPLATE_PAYMENT_SUCCESSFUL_UUID` | `orderId`, `paymentId`, `amount`, `currency` |
| `PAYMENT_FAILED` | `MAILTRAP_TEMPLATE_PAYMENT_FAILED_UUID` | `orderId`, `paymentId`, `amount`, `currency` |
| `ORDER_CANCELLED` | `MAILTRAP_TEMPLATE_ORDER_CANCELLED_UUID` | `orderId` |
| `REFUND_PROCESSED` | `MAILTRAP_TEMPLATE_REFUND_PROCESSED_UUID` | `orderId`, `paymentId`, `refundId`, `amount`, `currency` |
| `ORDER_SHIPPED` | `MAILTRAP_TEMPLATE_ORDER_SHIPPED_UUID` | `orderId` |
| `ORDER_DELIVERED` | `MAILTRAP_TEMPLATE_ORDER_DELIVERED_UUID` | `orderId` |
| `LOW_STOCK_WARNING` | `MAILTRAP_TEMPLATE_LOW_STOCK_WARNING_UUID` | `productId`, `availableStock` |
| `OUT_OF_STOCK_WARNING` | `MAILTRAP_TEMPLATE_OUT_OF_STOCK_WARNING_UUID` | `productId`, `availableStock` |
| `SELLER_NEW_ORDER` | `MAILTRAP_TEMPLATE_SELLER_NEW_ORDER_UUID` | `orderId` |

Every template also receives `appName` (`Pepekart`) and `notificationType`.
Commerce fields are supplied only when the upstream event includes them. Auth
links are generated when the email is sent and are never stored in the notification
payload. Use the variable names exactly as shown in Mailtrap, for example
`{{verificationUrl}}`, `{{resetUrl}}`, or `{{orderId}}`. A template's subject and
HTML are maintained in Mailtrap.

Out-of-stock emails are temporarily disabled by default while the catalogue has
many zero-stock products. Notification Service consumes these events without
creating emails and marks previously queued out-of-stock emails as `SKIPPED`.
Set `NOTIFICATION_OUT_OF_STOCK_EMAIL_ENABLED=true` and restart Notification Service
when those alerts are wanted again. Other notification types are unaffected.

## Test flow

```text
Register new user -> confirm user-contact-updated is consumed
-> check notification_recipients
-> create order/payment event
-> inspect Mailpit locally (or the selected provider in stage/production)
   and notification_deliveries
```

## Documentation

Detailed integration and design documentation is in [`docs/`](docs/README.md):

- [API and contracts](docs/api.md)
- [High-level design](docs/hld.md)
- [Low-level design](docs/lld.md)
- [Data model](docs/schema.md)
- [Events and operations](docs/events-and-operations.md)
