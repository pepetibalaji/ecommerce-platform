# Data Model

Flyway migrations create `payments`, `payment_attempts`, `payment_webhook_events`, and
`payment_refunds`. Payment links an order/user to provider status and amount. Attempts retain
checkout attempts, webhook events retain provider event IDs for idempotency, and refunds retain
refund state. Migration V2 adds payment idempotency and refund support.
