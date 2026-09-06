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
                 |     access-token blacklist and short-lived revocation state
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
- Run the OAuth/OIDC Authorization Server with database-backed clients, authorizations, and consents.
- Sign tokens with a stable RSA key from a keystore, HSM, or KMS JCA provider and expose its public key at `/oauth2/jwks`.
- Publish business events reliably through a transactional outbox.
- Produce immutable, privacy-safe audit records.

## Non-responsibilities

- Sending email: Notification Service owns delivery, retries, templates, and provider integration.
- User profile, addresses, orders, payments, carts, and product data.
- Per-request token introspection by business services. They validate JWT signatures through JWKS instead.

## Core state transitions

```text
PENDING_VERIFICATION -> ACTIVE -> SUSPENDED -> ACTIVE
          |                |          |
          +----------------+----------+-> DELETED (terminal)
```

Only an `ACTIVE` user with a non-null `email_verified_at` may receive or refresh a session.

Public registration, resend, and recovery endpoints must be rate-limited at the gateway/edge by IP and normalized email. Auth currently has no in-service Redis rate limiter; add one before relying on Auth alone for abuse protection.

