# Non-Functional Requirements

## Security

- Secrets, provider credentials, passwords, and JWTs must remain outside source control.
- Public requests enter through Gateway; services validate Auth-issued JWTs.
- Kafka events must not contain passwords, JWTs, card data, or provider secrets.
- `user-contact-updated` is the approved exception for email delivery and contains only the minimal recipient contact data.

## Reliability

- Services own their databases; no cross-service database foreign keys are allowed.
- Kafka consumers must be idempotent using event IDs.
- Notification intent must be persisted before email provider delivery.
- Notification retries use exponential backoff, jitter, and a configured maximum attempt count.
- Exhausted delivery is terminal (`FAILED`) and must be visible to operations.
- Order inventory-release work is durable and idempotent.

## Observability

- Every service exposes health and Prometheus endpoints.
- JSON logs include trace and correlation identifiers when available.
- Prometheus/Grafana alert on Notification delivery exhaustion and consumer lag.
- Operators use delivery-attempt history rather than provider dashboards alone for diagnosis.

## Quality and delivery

- Schema changes use Flyway migrations.
- Changed workflows require unit tests; integration-sensitive flows require Kafka/provider/database tests before stage promotion.
- CI must compile, test, and build a Docker image for every runnable service.
- Stage requires a verified email sender domain, Kafka topic/ACLs, database migration, and an end-to-end smoke test.
