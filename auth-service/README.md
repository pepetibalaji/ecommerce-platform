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

- `users`: identity, role, account status, token version.
- `refresh_tokens`: refresh-token lifecycle.
- Redis blacklist: revoked access-token IDs until expiry.

## End-to-end flow

```text
Register
  -> validate request and persist active user
  -> publish user-contact-updated(eventId, userId, email, active=true)
  -> issue access JWT + refresh token

Login
  -> verify BCrypt password and active status
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

Current: registration, login, refresh/logout, profile/admin users, user contact events. Next: recipient backfill for existing users, persistent signing keys, refresh-token hashing, MFA, and account recovery.

## Replacement design

The production redesign documentation is in [docs/README.md](docs/README.md). It covers the target high-level and low-level design, PostgreSQL schema, public API contract, Kafka events, and operational configuration.
