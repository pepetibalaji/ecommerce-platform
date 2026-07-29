# Payment Service

The Payment Service persists payment records, checkout attempts, provider webhook
events, and refunds. It consumes order-created events, creates provider checkout
sessions, processes verified provider webhooks, and publishes payment outcomes.

## Local configuration

Configuration is supplied through Config Server. Keep provider credentials out
of source control and inject them through environment variables or your local
Config Server configuration.

```yaml
payment:
  provider:
    active: SANDBOX # use STRIPE only with Stripe test-mode credentials
    mode: test
    sandbox:
      enabled: true
    stripe:
      enabled: false
      api-key: ${STRIPE_API_KEY:}
      webhook-secret: ${STRIPE_WEBHOOK_SECRET:}
  checkout:
    success-url: http://localhost:3000/payments/success?orderId={ORDER_ID}&paymentId={PAYMENT_ID}
    cancel-url: http://localhost:3000/payments/cancel?orderId={ORDER_ID}&paymentId={PAYMENT_ID}
```

Never log or commit provider API keys, webhook secrets, raw card data, or raw
provider webhook payloads.

## Interfaces

- REST checkout: `POST /api/v1/payments/orders/{orderId}/checkout-session`
- Customer history: `GET /api/v1/payments/me`
- Customer payment lookup: `GET /api/v1/payments/orders/{orderId}` and
  `GET /api/v1/payments/{paymentId}`
- Admin payment and refund APIs: `/api/v1/admin/payments/**`
- Provider webhooks: `/api/v1/payments/webhooks/stripe` and
  `/api/v1/payments/webhooks/razorpay`
- gRPC: `ProcessPayment`, `RefundPayment`, and `GetPaymentStatus`
- Kafka input: `order-created`
- Kafka outputs: `payment-success` and `payment-failed`, keyed by `orderId`

## Webhook and idempotency rules

Webhook signature verification is delegated to the active provider adapter.
Webhook events are persisted with a unique `(provider, provider_event_id)` key;
duplicates are acknowledged without publishing a duplicate payment outcome.

Payment creation is unique by order ID and idempotency key. Checkout session
creation reuses an active, unexpired attempt. Refund requests are idempotent by
their payment ID and idempotency key.

## Observability

The service exposes Spring Boot Actuator health and Prometheus endpoints, emits
OTLP traces, and writes structured JSON logs. Payment-specific metrics include:

- `payment.created.count`
- `payment.checkout_session.created.count`
- `payment.success.count`, `payment.failed.count`, and `payment.cancelled.count`
- `payment.refund.requested.count`, `payment.refund.success.count`, and
  `payment.refund.failed.count`
- `payment.provider.latency`
- `payment.webhook.received.count`, `payment.webhook.duplicate.count`, and
  `payment.webhook.invalid_signature.count`

Provider is the only custom metric label. Trace IDs, payment IDs, order IDs,
checkout URLs, and provider payloads must not be metric labels.

## Test commands

```bash
mvn -pl payment-service -am test
```

Use the `SANDBOX` provider for deterministic local tests. Stripe sandbox tests
require test-mode credentials and a Stripe CLI or public webhook endpoint; they
must never use live-mode credentials.

## Not included

Kafka retry/DLQ handling, an outbox pattern, Schema Registry, production live
money cutover, chargeback/dispute handling, fraud scoring, and distributed saga
orchestration are intentionally out of scope for this service stage.
