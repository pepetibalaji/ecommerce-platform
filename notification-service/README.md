# Notification Service

## What this service is

Notification Service runs on port `8088` and sends transactional customer/seller email asynchronously. It consumes Kafka events, stores notification state in its own PostgreSQL database, and sends through Mailtrap without blocking business workflows.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- Spring Kafka consumer
- Spring Mail SMTP and Mailtrap Email API adapters
- Spring Security OAuth2 Resource Server, Actuator, Prometheus

## Data owned

- `notifications`: one delivery intent and overall status.
- `notification_deliveries`: each provider attempt and error/reference.
- `notification_preferences`: user/channel/type preference state.
- `notification_processed_events`: Kafka idempotency.
- `notification_recipients`: local user ID, email, active state.

## End-to-end flow

```text
User registration
  -> Auth publishes user-contact-updated
  -> Notification Service upserts local recipient email

Payment/order event
  -> Notification Kafka consumer validates eventId
  -> insert one PENDING notification
  -> delivery worker reads active recipient locally
  -> send email through Mailtrap
  -> store provider message ID as SENT
  -> on failure, record attempt and retry with backoff + jitter
  -> after max attempts, mark FAILED and raise metric/alert
```

Supported now: order received, payment successful/failed, cancellation, and refund notification handling. Shipment, low inventory, and seller paid-order notifications wait for their upstream event producers.

## Run locally

```bash
cd notification-service
mvn spring-boot:run
```

Requires PostgreSQL, Kafka, Config Server, and Mailtrap Sandbox SMTP values from the separate config repository.

## Test flow

```text
Register new user -> confirm user-contact-updated is consumed
-> check notification_recipients
-> create order/payment event
-> inspect Mailtrap Sandbox inbox and notification_deliveries
```

## Current and next work

Current: durable email intent, idempotency, retry, Mailtrap adapters, recipient directory, metrics/admin visibility. Next: recipient backfill, end-to-end integration tests, stage sender-domain verification, fulfilment/inventory/seller event producers, SMS/push/in-app inbox.
