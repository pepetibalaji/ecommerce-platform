# Payment production reliability runbook

## Rollout prerequisites

Use the application branch with the matching configuration-repository changes and Order/frontend contract changes. Apply Flyway V4-V6 through normal startup. Confirm historical timestamp interpretation and reconcile any duplicate active attempts before V4; see [schema](schema.md).

Set the signed Order lookup shared secret on both services. Restrict its endpoint to trusted infrastructure and use the authenticated request/response signature contract. Configure authenticated Kafka infrastructure and topic ACLs: only Order principals produce Order commands, only Payment principals produce payment outcomes, and each consumer reads its required topics. Runtime services do not replace broker ACL administration.

Production/stage must use an approved HTTPS frontend origin. Set both checkout return URL templates to `https://<origin>/payment/return?orderId={ORDER_ID}&paymentId={PAYMENT_ID}`. Browser-controlled return URLs are not accepted. Set the exact frontend origin separately in the allowlist.

Razorpay must remain disabled. Sandbox is allowed only when every active profile is dev/local/test; a mixed prod+dev profile does not bypass this check. Enabling Stripe outside development requires the staging-verified flag after a staging checkout, signature/replay, expiry and refund exercise. Real provider verification is an operational release gate.

## Configuration

Application behavior is code here; environment configuration belongs in `C:\e-com\ecommerce-config-repo`.

| Property | Default / requirement |
| --- | --- |
| `payment.provider.active` | SANDBOX locally; STRIPE for an approved deployment |
| `payment.provider.sandbox.enabled` | true locally; must be false outside dev/local/test |
| `payment.provider.razorpay.enabled` | false; cannot be enabled |
| `payment.provider.stripe.enabled` | false until configured |
| `payment.provider.stripe.staging-verified` | false; required outside development when Stripe is enabled |
| `payment.provider.stripe.api-key` / `webhook-secret` | Secret injection; never commit values |
| `payment.provider.stripe.previous-webhook-secrets` | Empty list; temporary signature-rotation overlap |
| `payment.provider.stripe.timeout-ms` | 5000; requests use bounded connect/read timeouts |
| `payment.checkout.success-url` / `cancel-url` | Exact approved frontend route with both placeholders |
| `payment.checkout.frontend-origins` | Exact origin set; dev default http://localhost:5173 |
| `payment.checkout.allowed-provider-hosts` | checkout.stripe.com; exact HTTPS hosts, no suffix matching |
| `payment.order-lookup.base-url` / `secret` | Trusted internal Order endpoint / shared signing secret |
| `payment.outbox.poll-delay-ms` / `batch-size` | 1000 / 25 |
| `payment.outbox.max-attempts` / `send-timeout-seconds` | 12 / 10 |
| `payment.reconciliation.poll-delay-ms` | 60000 |
| `payment.expiry.poll-delay-ms` | 15000 |
| `payment.webhook.max-body-bytes` | 262144 |
| `payment.webhook.stripe-requests-per-minute` | 600 shared across replicas |
| `payment.webhook.retry-delay-ms` / `max-attempts` | 5000 / 12 |
| `payment.refunds.poll-delay-ms` / `batch-size` | 5000 / 20 |
| `payment.refunds.lease-seconds` / `max-attempts` | 120 / 10 |
| `payment.refunds.retry-base-seconds` | 30, exponential backoff capped at one hour |
| `payment.cancellations.poll-delay-ms` | 5000; up to 50 due commands per pass |

## Kafka delivery and dead records

Payment/refund status and the outbox row commit together. Financial outcome envelopes include event/payment/order/user IDs, amount, currency, provider, correlation/trace IDs and an aware timestamp. Refund-command rejection has a separate smaller envelope; see [event contracts](events-and-operations.md). Kafka keys use orderId. Outbox workers lease one row at a time with FOR UPDATE SKIP LOCKED and a unique lease token; a crashed lease becomes reclaimable. Backoff starts at two seconds and is bounded. Exhausted work stays DEAD as the durable dead-letter state.

The worker preserves order within an orderId: an earlier undelivered record blocks later outcomes, including when the earlier record is DEAD. This orders publication attempts; consumers still handle outcomes from different Kafka topics arriving in a different order. A broker acknowledgment followed by a database failure can cause redelivery; Order consumers must preserve their idempotency inbox and state checks.

Admin operations, all under `/api/v1/admin/payments/operations`:

- `GET /outbox`: counts and oldest creation time by delivery state.
- `POST /outbox/{eventId}/retry`: after correcting the cause, requeue a DEAD row with the same event ID.
- `GET /webhooks`: counts and oldest received time by processing state.
- `POST /webhooks/{eventId}/retry`: requeue a failed record only when a verified envelope was retained.

Use event IDs from structured logs or a restricted database query. These endpoints require an ADMIN token and log the acting identity. Never manufacture a new payment outcome ID to work around failed delivery. Reconciliation inserts missing business outcomes; existing pending/dead events are repaired through delivery/requeue, preserving their IDs.

## Webhook incidents

For invalid signatures, check the endpoint/account mode, configured signing secret, raw-body forwarding and server clock. Do not accept an unsigned callback. To rotate secrets, deploy the new active secret with the previous secret in the overlap list, verify delivery, then remove the previous secret after the provider's overlap period.

Unresolved records usually indicate delayed local/provider identifier persistence or inconsistent metadata. Compare only retained safe identifiers, Order identity/total and payload hash. A changed payload under an existing provider event ID is an anomaly. A terminal transition conflict remains manual review. Retry only after understanding the conflict; retries do not grant permission to override terminal states.

The service stores a SHA-256 of the raw payload and a whitelisted provider envelope, not raw card/payment data or provider error text. Do not add raw bodies or secrets to incident logs.

## Refund and cancellation incidents

Cancellation commands waiting for preparation or provider resolution remain `WAITING_PROVIDER`, retry every 30 seconds, and enter `MANUAL_REVIEW` after 24 hours from receipt. The cancellation scheduler processes up to 50 due records every five seconds by default. Investigate the retained command and payment/provider state before resolving review; do not bypass it with a customer-visible cancellation or inventory release.

Before-payment cancellation/expiry creates a durable tombstone and blocks checkout. Processing charges wait for verified resolution. Paid cancellation enters the refund workflow; Order cannot mark the paid order cancelled merely because the customer requested it.

Refund workers reserve funds under the Payment lock, replay uncertain provider creation using the saved key, and retrieve known provider refund IDs. Successful and pending/manual-review amounts count against the refundable balance. Partial refunds are admin-controlled. Public cancellation of a confirmed Order requests the entire immutable total. A partially refunded Order needs admin handling of its remaining balance; a competing partial refund can move the cancellation to review. If a pending cancellation races with payment success, the Payment cancellation worker calculates the remaining balance under the payment lock.

If provider acceptance remains unknown for 23 hours, or retry bounds are exhausted, work enters REFUND_MANUAL_REVIEW. This retains the amount reservation and alerts operations. Do not submit a new refund or change its provider idempotency key. For a known provider refund, use:

`POST /api/v1/admin/payments/{paymentId}/refunds/{refundId}/reconcile`

with a required investigation reason. The service records actor/time/reason and schedules status reconciliation. Unknown provider identity requires external investigation; the endpoint cannot safely invent it. Terminal provider results clear leases, so stale worker responses cannot change a webhook-completed refund.

Already deducted stock, fulfillment or shipping conflicts require Order/manual review. Late payment success after expiry/cancellation/failure also requires review, including provider-side refund investigation if money moved. Never silently resurrect the order or release/deduct inventory twice.

## Monitoring and verification

Monitor outbox delivery/retry/terminal counters and dead-count gauge; webhook invalid-signature, duplicate-anomaly, unresolved, transition-failure and state-conflict metrics; refund retries/manual-review; payment expiry; Kafka consumer dead letters and lag. Logs carry event/order/payment/refund IDs and safe error codes. Investigate a growing oldest undelivered age even if retries have not exhausted.

Run the coordinated reactor tests with PostgreSQL/Testcontainers available:

```powershell
mvn -pl payment-service,order-service,gateway-service -am test
npm --prefix frontend test
npm --prefix frontend run build
```

Database tests cover concurrent checkout and provider acceptance followed by local commit rollback/replay. Other tests exercise verified/late/duplicate webhooks, expiry, Kafka delivery failure, refunds/cancellation, ownership, safe errors, URL policy and frontend polling. Do not replace the database gate with silently skipped integration tests. These tests do not call a real Stripe account; record staging verification separately before enabling Stripe in a deployment.

The GitHub Actions workflow runs the full backend `verify` lifecycle with Docker available and separately installs, typechecks, tests and builds the frontend. After Maven succeeds, `java scripts/ci/VerifyPaymentReliabilityReports.java` requires the checkout, webhook/outbox, refund and Order-expiry PostgreSQL suites to have executed with no failures or skips. JVM reports are retained on successful and failed runs. CI builds the frontend with mocks disabled; its build guard rejects accidental mock enablement. These checks do not replace the real Stripe staging exercise.

The configuration repository has its own `Validate configuration` workflow. It checks all environment YAML files for syntax and duplicate keys, then validates the Payment/Order lookup bindings, scheduler/worker settings, provider gates and stage/prod Kafka security properties. Dotted and nested YAML properties are resolved before validation, so a correctly parsed but misplaced block cannot satisfy the required contract. The validator does not load `.env` files or verify deployed secret values.

## Environment and wire contract checklist

The external config branch uses the same `PAYMENT_ORDER_LOOKUP_SECRET` (at least 32 nonblank characters) for Order and Payment. Set `ORDER_SERVICE_URI` for deployed Payment, `PAYMENT_FRONTEND_ORIGINS` to exact frontend HTTPS origins, and the three frontend return URL settings. Stage/prod Kafka requires `SASL_SSL`, `KAFKA_SASL_MECHANISM` and secret-injected `KAFKA_SASL_JAAS_CONFIG`; use separate authorized service principals and enforce topic ACLs. Shared stage/prod Kafka transport settings apply to every service, so coordinate that infrastructure rollout.

Payment uses four scheduler threads so provider retries cannot occupy the only expiry/reconciliation thread. `ErrorHandlingDeserializer` delegates to the typed JSON deserializer, and each Payment command listener pins its schema class instead of trusting incoming type headers. Provision the new outcome/command topics and their `.DLT` destinations using `scripts/create-kafka-topics.sh` locally; use production replication/ACL settings in managed infrastructure. Malformed serialized records in a `.DLT` require inspection of retained original bytes before replay; do not reinterpret them as valid commands.

The [machine-readable outcome schema](payment-outcome.schema.json) uses ISO-8601 UTC `occurredAt`. Serialization pins string timestamps both in the durable outbox and on Kafka. Refund publication repairs a missing legacy payment-success outbox row before enqueueing the refund. Kafka topics can still be consumed in a different order; Order consumers enforce idempotency and lifecycle rules independently.

Prometheus rules are in `monitoring/prometheus-alerts.yml`. They cover DEAD outbox rows, signature failures, unresolved envelopes, transition conflicts, duplicate payload anomalies, refund/cancellation manual review and command DLQ delivery. Operators can also inspect oldest pending timestamps through the operations endpoints.

Stripe may prune idempotency keys after 24 hours, which is why unknown refund acceptance stops automatic replay after 23 hours; see [Stripe idempotency](https://docs.stripe.com/api/idempotent_requests). Known refund IDs use [authenticated refund retrieval](https://docs.stripe.com/api/refunds/retrieve). The lease queue uses PostgreSQL's [SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html) behavior for independent workers. Real Stripe staging verification is still required before setting `STRIPE_STAGING_VERIFIED=true`.
