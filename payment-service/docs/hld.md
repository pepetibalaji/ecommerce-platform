# Payment Service high-level design

Payment Service owns provider interaction, payment attempts, verified payment state and refunds. Order Service owns immutable totals, payable deadlines, fulfilment and inventory effects. Payment verifies signed trusted Order data rather than accepting totals from a browser or an unvalidated event.

```text
Order-created event -> trusted Order lookup -> idempotent Payment preparation
Customer checkout -> committed attempt reservation -> provider session using saved key
Signed provider webhook -> durable verified inbox -> locked state transition + outbox
Outbox worker -> Kafka, keyed by orderId -> idempotent Order lifecycle update
Order cancellation -> durable command inbox -> cancellation or durable refund work
Refund worker / verified refund webhook -> refund state + outbox -> Order outcome
```

Only verified webhooks confirm/fail a payment. Redirects and customer status requests only read authoritative state. Expiry and cancellation have explicit policy-driven terminal transitions under the same Payment lock; late paid outcomes after an unpaid terminal state require manual review.

Stripe is the implemented external provider, with staging verification required before deployment enablement. Sandbox is development/test only. Razorpay remains disabled. No raw card/payment payload or provider secrets belong in persisted webhook envelopes or customer responses.

See [current implementation](current-implementation.md) and [operational runbook](production-reliability.md).
