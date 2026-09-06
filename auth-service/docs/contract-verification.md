# Contract Verification and External Configuration

This is a source-level compatibility checklist for the replacement Auth Service. It records the contracts consumed by dependent services. The Testcontainers test covers Auth's PostgreSQL/Kafka outbox path when Docker is available; a deployed end-to-end verification is still required before release.

## JWT contract

| Claim | Required encoding | Current consumers | Release requirement |
| --- | --- | --- | --- |
| `iss` | Exact public Auth issuer URL | Every resource server validates the configured issuer. | `auth.authorization-server.issuer` and `AUTH_ISSUER_URI` must be the same public HTTPS URL in stage/prod. |
| `sub` | User email | Spring Security exposes it as `authentication.name`. | Keep it as the email until consumers deliberately migrate. Notification self-service authorization uses `userId`, not `sub`. |
| `userId` | UUID **string** | Cart preserves it as a string; Order, Payment, Product, and Inventory parse it as a UUID. | Emit it on direct-login and OAuth access tokens. |
| `role` | One deterministic legacy role string, e.g. `ADMIN` | Gateway and the common converters used by Cart, Order, Product, and Inventory; Payment and Notification also accept it. | Keep it while scalar-role consumers remain. Precedence is `ADMIN`, `SELLER`, then `CUSTOMER`. |
| `roles` | Array of role strings | Payment and Notification consume it; it is the target multi-role contract. | Emit it on direct-login and OAuth access tokens. |
| `permissions` | Array of permission strings | Auth and Notification map it to `PERMISSION_*`; other services do not yet enforce it. | Do not make cross-service authorization depend on it until their converters are updated. |
| `email_verified` | Boolean | No current downstream authorization decision consumes it. | Emit it from direct-login and OAuth tokens for future consumers. |
| `status`, `tokenVersion` | String / numeric | No downstream resource server currently enforces either value. | They are informational until every resource service implements token-version or revocation validation. |

Both direct-login and Authorization Server access tokens emit `userId`, `role`, `roles`, `permissions`, `email_verified`, `status`, and `tokenVersion`. `JwtContractTest` guards that shape for both issuance paths.

## Kafka and delivery-token contract

| Topic | Auth producer | Notification handling | Status |
| --- | --- | --- | --- |
| `auth.user-verification-requested.v1` | Registration/resend outbox; includes `eventId`, `userId`, `email`, `verificationActionId`, `occurredAt` | Listener creates a durable verification-email intent. | Implemented. |
| `auth.password-reset-requested.v1` | Reset-request outbox; includes `passwordResetActionId` | Listener creates a durable reset-email intent. | Implemented. |
| `auth.email-change-requested.v1` | Email-change request outbox; includes `emailChangeActionId` | Listener creates a durable new-email confirmation intent. | Implemented. |
| `auth.user-email-verified.v1`, `auth.password-reset.v1`, `auth.user-email-changed.v1` | Auth completion outbox events | No current in-repository consumer. | Provision only if an external consumer has agreed the schema; they are not used by Notification. |
| `user-contact-updated` | Auth outbox | `RecipientDirectoryService` | Compatible as plain JSON for `UserContactUpdatedEvent(eventId, userId, email, active, occurredAt)`. |

The Notification listener persists only the action id, recipient email, and non-secret event metadata. It rejects Kafka messages containing raw token fields. Its delivery worker fetches the token immediately before provider invocation, creates the public link in memory, and does not persist or log the token or link.

The protected lookup is:

```text
POST /internal/auth/actions/{actionId}/delivery-token
X-Internal-Auth: <AUTH_INTERNAL_SERVICE_TOKEN>
response: {"token":"..."}
```

Only Notification may call it over the private service network. Auth rejects a missing/incorrect secret using a constant-time comparison, fails startup in stage/prod when the secret is absent, and records denied/successful delivery-token requests in the Auth audit trail. The shared secret must come from the secret manager, be rotated, and be identical in Auth and Notification configuration.

`AuthOutboxPublisher` sends pre-serialized JSON with `KafkaTemplate<String, String>` and Notification consumes a `String`; the Auth producer must therefore use `StringSerializer` for values.

## Separate configuration repository

The separate configuration repository contains environment-specific Auth and Notification files. Checked-in files contain placeholders only; the secret manager supplies the values at deployment.

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
      properties:
        enable.idempotence: true

auth:
  authorization-server:
    issuer: ${AUTH_ISSUER_URI}
  internal:
    service-token: ${AUTH_INTERNAL_SERVICE_TOKEN}
  action-token:
    signing-secret: ${AUTH_ACTION_TOKEN_SIGNING_SECRET}
  signing-key:
    source: KEY_STORE
    key-id: ${AUTH_SIGNING_KEY_ID}
    key-store-location: ${AUTH_SIGNING_KEYSTORE_LOCATION}
    key-store-type: ${AUTH_SIGNING_KEYSTORE_TYPE:PKCS12}
    key-store-provider: ${AUTH_SIGNING_KEYSTORE_PROVIDER:}
    key-store-password: ${AUTH_SIGNING_KEYSTORE_PASSWORD}
    key-alias: ${AUTH_SIGNING_KEY_ALIAS}
    key-password: ${AUTH_SIGNING_KEY_PASSWORD}
    allow-ephemeral: false
  oauth:
    clients:
      - id: auth-web-client-v1
        client-id: ${AUTH_OAUTH_WEB_CLIENT_ID}
        client-secret-hash: ${AUTH_OAUTH_WEB_CLIENT_SECRET_HASH}
        client-name: ${AUTH_OAUTH_WEB_CLIENT_NAME:Ecommerce Web}
        authentication-methods: [client_secret_basic]
        grant-types: [authorization_code, refresh_token]
        redirect-uris: ["${AUTH_OAUTH_WEB_REDIRECT_URI}"]
        post-logout-redirect-uris: ["${AUTH_OAUTH_WEB_POST_LOGOUT_REDIRECT_URI}"]
        scopes: [openid, profile, email]
        require-authorization-consent: false
        require-proof-key: true
        access-token-ttl: 15m
        refresh-token-ttl: 7d
        reuse-refresh-tokens: false
```

`AUTH_ACTION_TOKEN_SIGNING_SECRET` must be at least 32 bytes. `AUTH_OAUTH_WEB_CLIENT_SECRET_HASH` must be a delegating `PasswordEncoder` value such as `{bcrypt}...`, not a raw client secret. Configured OAuth clients are inserted only when their client id is absent; changing YAML does not overwrite an existing database client.

For local dev, `source: GENERATED` with `allow-ephemeral: true` is acceptable. Stage and production require a stable RSA key from a keystore, HSM, or KMS JCA `KeyStore` provider. File/PKCS12 stores require `key-store-location`; a provider that initializes with `KeyStore.load(null, password)` may omit it but must expose an RSA private key and certificate under the configured alias. The current JWKS publishes one active key at `/oauth2/jwks`. Preserve that key across restarts; implement overlapping old/new JWK publication before enabling zero-downtime key rotation. `AUTH_JWK_SET_URI` belongs in dependent resource-service configuration, not Auth configuration.

Notification requires the same `AUTH_INTERNAL_SERVICE_TOKEN`, plus `AUTH_INTERNAL_BASE_URL`, `AUTH_EMAIL_VERIFICATION_URL`, `AUTH_PASSWORD_RESET_URL`, and `AUTH_EMAIL_CHANGE_URL`. Auth also needs its normal database, Redis, Kafka TLS/SASL, and secret-manager configuration.

## Cross-service verification before release

1. Run `JwtContractTest` and obtain one direct-login token and one OAuth access token; assert the JWT table above.
2. Call authenticated Cart, Order, Payment, Product, Inventory, and Notification endpoints through the gateway with each token.
3. Register, request a reset, and request an email change; assert Notification consumes each request, calls the protected endpoint with the shared secret, and sends a link without storing its token.
4. Confirm each action and assert `user-contact-updated` updates Notification's recipient directory exactly once under duplicate delivery.
5. Run `AuthOutboxPostgresKafkaIntegrationTest` in Docker-enabled CI. It verifies Flyway's PostgreSQL schema, PostgreSQL `INET` bindings, durable outbox persistence, and `user-contact-updated` Kafka JSON delivery.
6. Restart Auth with the same signing key and verify old tokens remain verifiable through the unchanged issuer/JWKS key.
