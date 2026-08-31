# Notification Service

Notification delivery service with recipient preferences and delivery history.

API base path: `/api/v1/notifications`. It consumes order, payment, and user-contact Kafka events and requires PostgreSQL, Kafka, OAuth, and email-provider configuration.
