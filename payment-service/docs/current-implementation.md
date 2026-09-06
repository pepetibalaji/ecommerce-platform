# Payment Service current implementation

## Implemented

* Idempotent payment preparation from `order-created`.
* Customer-owned checkout-session and payment-query endpoints.
* Sandbox, Stripe, and Razorpay adapter structure; signed Stripe/Razorpay webhook processing.
* Persistent checkout attempts, webhook inbox, refunds, and optimistic payment versioning.
* Admin payment visibility and idempotent refund initiation.
* Payment success/failure/refund Kafka outcomes, gRPC server, OpenAPI, Actuator, metrics, and tracing dependencies.

## Important limitations

* Browser success/cancel routes are informational; only webhooks change payment state.
* Payment persistence and Kafka outcome publishing have no transactional outbox guarantee.
* No general retry scheduler/cancellation policy for provider work is implemented here.
* Payment amount/currency are trusted from Order Service event and are not independently revalidated against the order.
* Provider secrets and raw payloads require careful deployment/logging hygiene.
