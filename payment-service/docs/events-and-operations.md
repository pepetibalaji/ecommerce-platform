# Events and Operations

Consumes `order-created` in the payment consumer group. Publishes `payment-success`,
`payment-failed`, and `payment-refund-completed`. Configure PostgreSQL, Kafka, OAuth/JWK, provider
credentials/webhook secrets, redirect URLs, and gRPC. Alert on webhook verification failures,
payment event publication failures, retry/DLT growth, and provider callback latency. Never log
provider secrets or raw card/payment payloads.
