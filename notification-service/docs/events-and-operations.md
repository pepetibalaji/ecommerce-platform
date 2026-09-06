# Notification Service events and operations

## Kafka inputs

Consumer group defaults to `notification-service`. Business topics: `order-created`, `payment-success`, `payment-failed`, `order-cancelled`, `payment-refund-completed`, `order-shipped`, `order-delivered`, `low-inventory`, and `seller-order-paid`. It also consumes `user-contact-updated` plus Auth verification/password-reset/email-change request topics.

Business payloads must have an `eventId`. Most require `userId`; low-inventory uses `sellerUserId` or `adminUserId`. Notification Service currently produces no Kafka events.

## Configuration and monitoring

`SPRING_PROFILES_ACTIVE` defaults `dev`; `CONFIG_SERVER_URL` defaults `http://localhost:8888`; `OBSERVABILITY_LOG_FILE` defaults `../logs/notification-service.json`. Configure PostgreSQL/Flyway, Kafka, selected email provider, Auth internal URL/token, and frontend verification/reset/email-change URLs. Delivery poll period, base delay, maximum attempts, and provider choice come from `notification.*` configuration.

Monitor Kafka lag/error rate, PENDING age, FAILED notifications, `notification_delivery_exhausted_total`, provider latency/error rate, recipient-directory freshness, and token-client failures. The failed-delivery admin API assists investigation. Never log addresses unnecessarily, provider credentials, internal-service tokens, or action URLs/tokens.

For outages, restore provider/Auth dependencies, then allow scheduled retry for PENDING records. FAILED records need operator remediation/requeue support (no admin retry endpoint is implemented). Correct bad event shapes/upstream recipient fields before replaying because processed event IDs suppress duplicates.
