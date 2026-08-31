# Events, Configuration, and Operations

## Kafka events

| Topic | Producer | Consumer | Purpose |
| --- | --- | --- | --- |
| `auth.user-verification-requested.v1` | Auth | Notification | Request verification-email delivery. |
| `auth.password-reset-requested.v1` | Auth | Notification | Request reset-email delivery. |
| `auth.user-email-verified.v1` | Auth | Platform consumers | Announce successful verification. |
| `user-contact-updated.v1` | Auth | Notification | Update recipient directory after activation, email change, suspension, or deletion. |

Example verification event. Do not include a raw token or full verification URL:

```json
{
  "eventId": "uuid",
  "eventType": "auth.user-verification-requested.v1",
  "userId": "uuid",
  "email": "jane@example.com",
  "verificationActionId": "uuid",
  "occurredAt": "2026-08-31T00:00:00Z"
}
```

Notification Service can obtain a short-lived delivery link from a protected internal Auth endpoint immediately before sending the message. This avoids retaining usable secrets in Kafka or notification storage.

## Required configuration

Inject these through the configuration repository and secret manager; do not commit values to source control.

```text
DATABASE_URL, DATABASE_USERNAME, DATABASE_PASSWORD
REDIS_HOST, REDIS_PASSWORD
KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_*
JWT_ISSUER, JWT_AUDIENCE, JWT_SIGNING_KEY or KMS key reference
FRONTEND_BASE_URL
INTERNAL_SERVICE_AUTH credentials
EMAIL_VERIFICATION_TTL_MINUTES
PASSWORD_RESET_TTL_MINUTES
```

## Production checklist

- Flyway migrations run before application traffic is enabled.
- PostgreSQL backups are encrypted and restore-tested.
- Kafka uses TLS, ACLs, retention limits, retry topics, and DLQs.
- Signing keys are persisted or managed by KMS and have an explicit rotation policy.
- Actuator health checks cover PostgreSQL, Redis, and Kafka connectivity.
- Logs mask email addresses where practical and never include credentials or token values.
- Metrics track registration, verification, reset, login failures, outbox age, Kafka failures, and rate-limit denials.
- Alert when the oldest unpublished outbox event exceeds its delivery objective.

