# Payment Service

Payment orchestration for checkout, webhooks, refunds, and payment events.

Customer endpoints use `/api/v1/payments`; callbacks use `/api/v1/payments/webhooks`; admin endpoints use `/api/v1/admin/payments`. It consumes `order-created` and publishes payment outcomes.
