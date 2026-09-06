# Auth Service

## What this service is

Auth Service owns user identity and account lifecycle. It runs on port `8081`, issues access/refresh tokens, handles registration/login/logout, and provides customer/admin user management.

## Technology

- Java 21, Spring Boot, Spring Security
- Spring Authorization Server and OAuth2 Resource Server
- PostgreSQL + Spring Data JPA + Flyway
- Redis for token blacklist
- Kafka producer for user-contact events
- Actuator, OpenAPI, structured logs

## Data owned

- `users`, roles, and permissions: identity, RBAC, account status, and token version.
- `refresh_sessions`: hashed, rotating refresh-token lifecycle.
- `identity_action_tokens`: hashed email-verification, reset, and email-change actions.
- `auth_outbox_events` and `auth_audit_events`: reliable event delivery and security/admin audit history.
- Spring Authorization Server clients, authorizations, and consents.
- Redis blacklist: revoked access-token IDs until expiry.

## End-to-end flow

```text
Register
  -> persist PENDING_VERIFICATION user
  -> publish token-free verification-request event
  -> Notification obtains the delivery token privately and sends confirmation email
  -> confirmation activates the user and publishes user-contact-updated

Login
  -> verify BCrypt password, ACTIVE status, and verified email
  -> issue access JWT + refresh token

Delete/deactivate
  -> mark user DELETED, revoke sessions
  -> publish user-contact-updated(... active=false)
```

Notification Service consumes the contact event into its own recipient directory. No password or JWT is sent to Kafka.

## Run locally

```bash
cd auth-service
mvn spring-boot:run
```

Requires PostgreSQL, Redis, Kafka, Config Server, and Auth database configuration.

## Current and next work

Current: verified registration/resend, login, refresh/logout, reset, verified email change, profile/admin users, persistent OAuth state, stable signing-key support, audit events, and token-free Notification delivery. Future hardening includes MFA, an Auth-local rate limiter, multi-instance outbox leasing/DLQ, and overlapping-JWK key rotation.

## Replacement design

The production redesign documentation is in [docs/README.md](docs/README.md). It covers the target high-level and low-level design, PostgreSQL schema, public API contract, Kafka events, and operational configuration.
