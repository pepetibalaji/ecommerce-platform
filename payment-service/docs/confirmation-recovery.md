# Payment confirmation and recovery

A browser return, URL parameter or provider status lookup never confirms/fails a payment. Payment becomes SUCCESS/FAILED only through a verified provider webhook. `POST /api/v1/payments/orders/{orderId}/refresh` remains an authenticated compatibility read of persisted state.

The frontend return route authenticates the owner and reads both Order and Payment APIs. It checks up to 25 times over about two minutes, stops when the view is abandoned or reaches a terminal result, and offers a status retry after exhaustion. It never asks the customer to create a second checkout merely because confirmation is delayed.

Recovery depends on durable records:

1. If Payment is missing, use the bounded PAYMENT_PREPARING retry. Investigate the Order-created consumer, signed Order lookup and Kafka dead-letter records if preparation stays delayed.
2. If provider creation timed out, retry the same checkout endpoint while its attempt remains eligible. The committed reservation replays identical parameters and the same provider idempotency key.
3. If a webhook arrived before local provider identifiers were saved, its signed payment/attempt metadata resolves the durable reservation. Unresolved/failed verified records retry from the retained safe envelope.
4. If Payment is terminal but Order is stale, inspect the payment outbox and consumer health. Kafka outages never roll back an already committed outcome; the worker retries the existing event.
5. If a session is abandoned, the expiry worker produces EXPIRED without relying on the provider webhook. Late success after expiry/cancellation/failure is flagged for manual review; do not resurrect the order or reassign stock automatically.
6. If refund acceptance is uncertain, keep the reserved amount and let durable reconciliation run. Known provider refund IDs can be reconciled by an audited admin request. Unknown acceptance beyond the safe provider-key window requires investigation.

Configure Stripe Checkout completion, delayed-success, delayed-failure, expiry and supported refund webhooks on the Gateway's `/api/v1/payments/webhooks/stripe` route. Use the matching endpoint secret and account mode. Signed checkout fields remain readable across SDK API-version mismatches without converting a status lookup into payment proof.

Legacy `/public/payments/success` and `/cancel` only redirect to an approved frontend payment return route. New provider sessions target the frontend directly.

See [API errors and polling contract](api.md), [migration prerequisites](schema.md), and [operational procedures](production-reliability.md).
