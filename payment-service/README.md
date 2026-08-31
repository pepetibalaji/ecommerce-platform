# Payment Service

## What this service is

Payment Service owns payment state and provider integration. It runs on REST port `8087` and gRPC port `9092`. It prepares payments from order events, creates checkout sessions, processes signed provider webhooks, issues refunds, and publishes payment events.

## Technology

- Java 21, Spring Boot, Spring MVC
- PostgreSQL + JPA + Flyway
- Kafka producer/consumer
- gRPC server
- Payment adapters: Sandbox, Stripe, Razorpay
- Spring Security OAuth2 Resource Server, Actuator, OpenAPI

## Data owned

- Payments, checkout attempts, refunds, and provider webhook records.

## End-to-end flow

```text
order-created Kafka event
  -> create/reuse pending payment

Customer checkout request
  -> verify payment ownership
  -> create/reuse provider checkout session
  -> return provider URL

Provider webhook
  -> verify signature and deduplicate provider event
  -> persist payment state
  -> publish payment-success, payment-failed, or refund-completed
```

Order and Notification Service consume these events independently.

## Run locally

```bash
cd payment-service
mvn spring-boot:run
```

Requires PostgreSQL, Kafka, Config Server, Auth issuer/JWK configuration, and provider credentials for non-sandbox modes.

## Current and next work

Current: payment preparation, checkout, webhook processing, refund support, event publishing. Next: transactional outbox, complete retry/cancellation policy, and stage provider/webhook validation.
