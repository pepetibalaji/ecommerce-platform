# Low-Level Design

`Payment`, `PaymentAttempt`, `PaymentWebhookEvent`, and `PaymentRefund` are durable JPA entities.
Webhook event persistence provides provider-event idempotency. The order-created Kafka consumer
drives payment creation; checkout creates a provider session; verified webhooks transition payment
state and publish success/failure/refund events. `PaymentGrpcService` provides internal payment
operations. Controller access is separated into customer, public redirect, webhook, and admin APIs.
