# Low-Level Design

## Package structure

```text
api/          controllers and request/response DTOs
domain/       JPA entities and enums
repository/   data access
service/      registration, verification, sessions, reset, RBAC
security/     JWT, JWKS, OAuth server, hashing, rate limiting
messaging/    outbox entities, poller, event factory, Kafka producer
audit/        audit writer and query support
```

## Registration transaction

```text
1. Normalize email and rate-limit request.
2. Validate uniqueness.
3. Hash password using Argon2id.
4. Insert user with PENDING_VERIFICATION state.
5. Generate a cryptographically random action token.
6. Insert only its hash in identity_action_tokens.
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
2. Update the Argon2id password hash and `password_changed_at`.
3. Revoke all refresh sessions.
4. Increment `token_version`.
5. Write `PASSWORD_RESET` audit event.

## Profile and administration

Self-service profile updates may change only safe display fields directly. Email changes require an `EMAIL_CHANGE` action token and a confirmation link sent to the proposed address. Password changes require the current password and invalidate all sessions.

Administrative status and role changes run in one transaction: update the user or role assignments, revoke refresh sessions, increment `token_version`, add an audit row, and write an outbox event. This prevents an old access token from retaining access after a suspension or privilege change.

## Outbox publisher

A scheduled worker claims unpublished rows, publishes to Kafka using `event_key`, then sets `published_at` only after broker acknowledgement. It retries failures with bounded attempts and forwards exhausted messages to a dead-letter process. Consumers must deduplicate with `eventId`.

## Security rules

- Use Argon2id for passwords; never encrypt or log them.
- Generate at least 32 random bytes for opaque action and refresh tokens; persist only hashes.
- Use TLS for HTTP, PostgreSQL, Redis, and Kafka.
- Use Redis for rate limits and short-lived anti-abuse counters.
- Return generic success responses for reset/resend operations.
- Use configured trusted frontend URLs when building action links; never use the request `Host` header.
- Do not expose raw tokens in Kafka payloads, notification persistence, logs, traces, browser referrers, or audit records.
