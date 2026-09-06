# Events, Configuration, and Operations

## Kafka events

| Topic | Producer | Consumer | Purpose |
| --- | --- | --- | --- |
| `auth.user-verification-requested.v1` | Auth | Notification | Request verification-email delivery. |
| `auth.password-reset-requested.v1` | Auth | Notification | Request reset-email delivery. |
| `auth.email-change-requested.v1` | Auth | Notification | Request new-email confirmation delivery. |
| `auth.user-email-verified.v1` | Auth | No current in-repository consumer | Announce successful verification for an explicitly contracted future consumer. |
| `auth.password-reset.v1` | Auth | No current in-repository consumer | Announce successful password reset for an explicitly contracted future consumer. |
| `auth.user-email-changed.v1` | Auth | No current in-repository consumer | Announce successful email change for an explicitly contracted future consumer. |
| `user-contact-updated` | Auth | Notification | Update recipient directory after verification, email change, or any admin status transition. |

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

Notification Service obtains a raw opaque action token from a protected internal Auth endpoint immediately before provider invocation, then builds the public delivery link in memory. This avoids retaining usable secrets in Kafka or notification storage.

## Required configuration

Inject these through the configuration repository and secret manager; do not commit values to source control.

```text
AUTH_DB_URL, AUTH_DB_USERNAME, AUTH_DB_PASSWORD
REDIS_HOST, REDIS_PASSWORD
KAFKA_BOOTSTRAP_SERVERS, KAFKA_SECURITY_PROTOCOL, KAFKA_SASL_*
AUTH_ISSUER_URI
AUTH_INTERNAL_SERVICE_TOKEN
AUTH_ACTION_TOKEN_SIGNING_SECRET (at least 32 bytes)
AUTH_SIGNING_KEY_ID, AUTH_SIGNING_KEYSTORE_LOCATION, AUTH_SIGNING_KEYSTORE_PASSWORD
AUTH_SIGNING_KEY_ALIAS, AUTH_SIGNING_KEY_PASSWORD, AUTH_SIGNING_KEYSTORE_PROVIDER (for KMS/HSM when applicable)
AUTH_OAUTH_WEB_CLIENT_ID, AUTH_OAUTH_WEB_CLIENT_SECRET_HASH, AUTH_OAUTH_WEB_REDIRECT_URI
AUTH_INTERNAL_BASE_URL, AUTH_EMAIL_VERIFICATION_URL, AUTH_PASSWORD_RESET_URL, AUTH_EMAIL_CHANGE_URL
```

Identity-action TTL is currently fixed at 30 minutes in code. The listed `AUTH_EMAIL_*` values are supplied to Notification, while Auth receives the `AUTH_*` database/OAuth/signing values.

## Production checklist

- Flyway migrations run before application traffic is enabled.
- PostgreSQL backups are encrypted and restore-tested.
- Kafka uses TLS, ACLs, retention limits, retry topics, and DLQs.
- Signing keys are persisted or managed by KMS and retain the same active key across restarts. Add overlapping old/new JWK publication before zero-downtime key rotation.
- Actuator health checks cover PostgreSQL, Redis, and Kafka connectivity.
- Logs mask email addresses where practical and never include credentials or token values.
- Metrics track registration, verification, reset, login failures, outbox age, and Kafka failures; edge rate-limit telemetry is owned by the gateway until Auth gains its own limiter.
- Alert when the oldest unpublished outbox event exceeds its delivery objective.

