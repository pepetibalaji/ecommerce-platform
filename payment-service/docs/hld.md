# High-Level Design

Payment Service owns payment records and provider-facing payment lifecycle work. It receives an
order-created event, creates/updates payment state, exposes customer checkout/status APIs, accepts
provider webhooks, and publishes payment outcome events for Order Service.

Components: REST controllers, `PaymentOrderCreatedConsumer`, checkout/payment/webhook/refund
services, provider adapters, PostgreSQL repositories, Kafka publisher, and a gRPC payment server.
