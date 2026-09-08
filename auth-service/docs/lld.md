# Low-Level Design

## Package structure

```text
controller/   REST controllers
dto/          request and response DTOs
entity/       JPA entities and enums
repository/   data access
service/      registration, verification, sessions, reset, RBAC
security/     JWT validation, internal service authorization, and blacklist support
config/       OAuth Authorization Server, signing-key, and Spring Security configuration
messaging/    outbox publisher and Kafka producer
```

Audit entities live under `entity` and the audit writer/context live under `service`.

## Registration transaction

```text
1. Normalize and validate the email. Gateway/edge rate limiting is required but not currently implemented in Auth.
2. Validate uniqueness.
3. Hash password using the configured adaptive password encoder (currently BCrypt).
4. Insert user with PENDING_VERIFICATION state.
5. Generate an opaque HMAC-derived action token from the random action id and the secret-manager signing secret.
6. Insert only its SHA-256 verifier in identity_action_tokens.
7. Insert auth.user-verification-requested.v1 into auth_outbox_events.
8. Commit one database transaction.
```

No access or refresh token is issued at this point.

## Verification transaction

```text
1. Hash submitted token.
2. Atomically claim an unexpired, unconsumed EMAIL_VERIFICATION row.
3. Mark token consumed.
4. Set users.status = ACTIVE and email_verified_at = now().
5. Insert EMAIL_VERIFIED audit event.
6. Insert user-email-verified and user-contact-updated outbox events.
7. Commit.
```

Use `SELECT ... FOR UPDATE` or an equivalent conditional update so a token cannot be consumed twice.

## Login and refresh

Login checks the password, `ACTIVE` status, and verified email. It creates a `refresh_sessions` row using only a token hash and issues a short-lived signed JWT.

Each refresh request rotates the opaque refresh token. Revoke the old session and create its replacement in the same `token_family_id`. If an old revoked token is presented again, revoke the whole family and increment `users.token_version`.

## Password reset

Password reset creates a `PASSWORD_RESET` action through the outbox, exactly like email verification. On successful reset:

1. Consume the action token.
2. Update the adaptive password hash and `password_changed_at`.
3. Revoke all refresh sessions.
4. Increment `token_version`.
5. Write `PASSWORD_RESET` audit event.

## Profile and administration

Self-service profile updates may change only safe display fields directly. Email changes require an `EMAIL_CHANGE` action token and a confirmation link sent to the proposed address. Password changes require the current password and invalidate all sessions.

Administrative status and role changes run in one transaction: update the user or role assignments, revoke refresh sessions, increment `token_version`, publish the new version to Redis, and add an audit row. Status changes also enqueue `user-contact-updated`; role changes currently publish no role-change event. Resource services using `common-security` reject a JWT whose `tokenVersion` differs from Redis; they must have private Redis connectivity for this immediate invalidation control to apply.

## Outbox publisher

A scheduled worker claims up to 100 eligible rows with PostgreSQL `FOR UPDATE SKIP LOCKED`, leases each claim, synchronously publishes using `event_key`, and sets `published_at` only after broker acknowledgement. Failures use exponential backoff and a non-secret error class; terminal failures are marked dead-lettered after the configured attempt limit and can be replayed through the admin recovery endpoint. Consumers must deduplicate with `eventId`.

## Security rules

- Use the configured adaptive password encoder; never encrypt or log passwords.
- Generate at least 32 random bytes for opaque refresh tokens. Action tokens are HMAC-derived from random UUID action ids and a secret-manager key; persist only SHA-256 verifiers.
- Use TLS for HTTP, PostgreSQL, Redis, and Kafka.
- Keep gateway/edge limits as the first line of defence; Auth also enforces its Redis-backed limits for public identity actions.
- Return generic success responses for reset/resend operations.
- Use configured trusted frontend URLs when building action links; never use the request `Host` header.
- Do not expose raw tokens in Kafka payloads, notification persistence, logs, traces, browser referrers, or audit records.
