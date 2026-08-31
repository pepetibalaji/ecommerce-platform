# High-Level Design

## Purpose

Auth Service is the identity provider for the ecommerce platform. It owns user identity, credentials, email verification, roles, sessions, password recovery, JWT issuance, and authentication audit history. No other service may read or write its database.

## System context

```text
Client
  |
  v
Gateway --> Auth Service --> PostgreSQL
                 |             users, roles, sessions,
                 |             action tokens, outbox, audit
                 |
                 +--> Redis
                 |     rate limits and short-lived security state
                 |
                 +--> Kafka --> Notification Service --> Email provider
                 |
                 +--> JWKS endpoint --> Gateway and business services
                                      validate JWTs locally
```

## Responsibilities

- Register users in `PENDING_VERIFICATION` state.
- Verify email ownership and activate accounts.
- Authenticate active users and issue JWT access tokens.
- Rotate refresh tokens and detect token reuse.
- Reset passwords and revoke sessions after a reset.
- Enforce role- and permission-based authorization data.
- Publish business events reliably through a transactional outbox.
- Produce immutable, privacy-safe audit records.

## Non-responsibilities

- Sending email: Notification Service owns delivery, retries, templates, and provider integration.
- User profile, addresses, orders, payments, carts, and product data.
- Per-request token introspection by business services. They validate JWT signatures through JWKS instead.

## Core state transitions

```text
Registration -> PENDING_VERIFICATION -> ACTIVE
                                   |       |
                                   |       +-> SUSPENDED
                                   +----------> DELETED
```

Only an `ACTIVE` user with a non-null `email_verified_at` may receive or refresh a session.

