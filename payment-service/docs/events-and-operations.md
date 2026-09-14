# Payment events and operations

Payment consumes authenticated Order-created, cancellation/expiry and refund-request contracts. Event schema and required identity/amount/currency fields are validated; payment preparation independently verifies the signed trusted Order snapshot.

Financial outcomes include `payment-success`, `payment-failed`, `payment-expired`, `payment-refund-completed` and `payment-refund-failed`. These envelopes carry event/payment/order/user IDs, amount/currency, provider, correlation/trace IDs and UTC timestamps; refund outcomes carry refund identity and cumulative amounts where applicable. The [payment outcome schema](payment-outcome.schema.json) covers these five types.

`payment-refund-request-rejected` separately reports a business refusal of an Order refund command through the same durable outbox. Its `PAYMENT_REFUND_REQUEST_REJECTED` envelope carries `eventId`, `eventType`, `schemaVersion`, `source`, `occurredAt`, `correlationId`, `traceId`, `refundRequestId`, `paymentId`, `orderId` and safe `reason`. The event ID is the original refund request ID. It does not carry user, amount, currency or provider fields and is outside the five-type financial outcome schema. A rejection does not prove that a provider refund occurred.

Payment/refund state and its outbox event commit together. Delivery uses orderId as Kafka key, per-order publication sequencing, leases, bounded retries and a durable DEAD state. Different Kafka topics may still be consumed in a different order. Delivery is at least once; Order consumers must deduplicate and reject contradictory/late lifecycle changes. Retry existing event IDs rather than synthesizing new outcomes.

Use the [production reliability runbook](production-reliability.md) for environment settings, Kafka security, webhook secret rotation, incident metrics, outbox/webhook retry endpoints, and safe refund reconciliation. See [API](api.md) for customer contracts and [schema](schema.md) for durable record definitions.
