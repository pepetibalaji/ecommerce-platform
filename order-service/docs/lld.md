# Low-Level Design

`OrderServiceImpl` resolves each product through `ProductSellerClient`, calculates totals, reserves
through gRPC, persists the order, and publishes `order-created`. Failed creation compensates
reservations. Payment Kafka consumers update state idempotently using `OrderProcessedEvent`; an
inventory-release outbox retries cancellation/refund releases.
