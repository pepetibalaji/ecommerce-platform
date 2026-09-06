# Payment Service events and operations

## Kafka

Consumes `order-created` using group `payment-service` by default and creates a PENDING payment with idempotency key `order-created:{orderId}`. Publishes `payment-success`, `payment-failed`, and `payment-refund-completed` when verified provider processing changes the relevant state.

## Configuration and safety

Required environment configuration includes PostgreSQL, Kafka, JWT/JWK, active provider choice/credentials, Stripe/Razorpay webhook secrets, redirect URLs, gRPC, Config Server, and tracing. `SPRING_PROFILES_ACTIVE` defaults `dev`; `CONFIG_SERVER_URL` defaults `http://localhost:8888`; `OBSERVABILITY_LOG_FILE` defaults `../logs/payment-service.json`.

Public utility endpoints include health/info/prometheus and OpenAPI/Swagger. Monitor provider latency, invalid signature/webhook counts, payment-event publication errors, consumer lag, payment/refund state anomalies, and expired checkout attempts. Never log secrets, signatures, card data, or raw sensitive provider payloads.

When Kafka is unavailable, reconcile payment records whose terminal state lacks the expected downstream outcome; when a provider callback fails validation, correct provider secret/configuration before replaying according to provider rules.
