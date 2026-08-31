# Database Schema

Use PostgreSQL, UUID primary keys, `TIMESTAMPTZ` for all instants, and Flyway versioned migrations. Passwords, refresh tokens, and action tokens must never be stored in plaintext.

## Identity and authorization

### `users`

```text
id UUID PK
email VARCHAR(255)                    -- entered/display value
email_normalized VARCHAR(255) UNIQUE  -- normalized lookup value
password_hash VARCHAR(255)
display_name VARCHAR(150)
status VARCHAR(32)
email_verified_at TIMESTAMPTZ NULL
password_changed_at TIMESTAMPTZ NULL
token_version BIGINT NOT NULL DEFAULT 0
created_at TIMESTAMPTZ
updated_at TIMESTAMPTZ
deleted_at TIMESTAMPTZ NULL
```

Valid statuses: `PENDING_VERIFICATION`, `ACTIVE`, `SUSPENDED`, `DELETED`. Keep `email_normalized` unique and preserve original email separately when exact display casing matters.

### RBAC

```text
roles(id PK, code UNIQUE, description, system_role, created_at, updated_at)
permissions(id PK, code UNIQUE, description, created_at)
user_roles(user_id FK, role_id FK, assigned_at, assigned_by FK NULL, PK(user_id, role_id))
role_permissions(role_id FK, permission_id FK, granted_at, PK(role_id, permission_id))
```

Seed system roles: `CUSTOMER`, `SELLER`, `ADMIN`. Example permission codes: `PRODUCT:WRITE`, `USER:SUSPEND`, `ORDER:READ_ALL`.

## Credentials and account actions

### `refresh_sessions`

```text
id UUID PK
user_id UUID FK -> users
token_hash VARCHAR(128) UNIQUE
token_family_id UUID
expires_at TIMESTAMPTZ
revoked_at TIMESTAMPTZ NULL
replaced_by_session_id UUID NULL FK -> refresh_sessions
created_at TIMESTAMPTZ
last_used_at TIMESTAMPTZ NULL
device_name VARCHAR(255) NULL
ip_address INET NULL
user_agent VARCHAR(512) NULL
```

Indexes: `(user_id, expires_at) WHERE revoked_at IS NULL` and `(token_family_id)`.

### `identity_action_tokens`

This one table supports email verification, password reset, and email changes.

```text
id UUID PK
user_id UUID FK -> users
token_hash VARCHAR(128) UNIQUE
action_type VARCHAR(40)
target_email VARCHAR(255) NULL
expires_at TIMESTAMPTZ
consumed_at TIMESTAMPTZ NULL
created_at TIMESTAMPTZ
requested_ip INET NULL
user_agent VARCHAR(512) NULL
```

Allowed actions: `EMAIL_VERIFICATION`, `PASSWORD_RESET`, `EMAIL_CHANGE`. Index active actions with `(user_id, expires_at) WHERE consumed_at IS NULL`.

## Reliability and audit

### `auth_outbox_events`

```text
id UUID PK
aggregate_type VARCHAR(64)
aggregate_id UUID
event_type VARCHAR(128)
topic VARCHAR(255)
event_key VARCHAR(255)
payload JSONB
created_at TIMESTAMPTZ
published_at TIMESTAMPTZ NULL
attempts INTEGER DEFAULT 0
last_error TEXT NULL
```

Index unpublished rows with `(created_at) WHERE published_at IS NULL`.

### `auth_audit_events`

```text
id UUID PK
actor_user_id UUID NULL FK -> users
subject_user_id UUID NULL FK -> users
event_type VARCHAR(128)
outcome VARCHAR(16)
ip_address INET NULL
user_agent VARCHAR(512) NULL
metadata JSONB NULL
created_at TIMESTAMPTZ
```

Use outcomes `SUCCESS`, `FAILURE`, and `DENIED`. Do not write raw tokens, passwords, or complete action URLs to audit metadata.

## Migration order

1. Users and RBAC tables.
2. Action tokens and refresh sessions.
3. Outbox and audit tables.
4. OAuth persistence tables when Spring Authorization Server is enabled with database-backed clients and authorizations.
5. Backfill legacy user UUIDs, normalized emails, roles, and active verification state before cutover.

