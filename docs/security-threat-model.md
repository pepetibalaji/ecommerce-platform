# Security Design and Threat Model

**Status:** Current controls and required hardening  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Security objectives

Protect customer identity, credentials, personal data, payments, inventory, and
administrative operations while preserving traceability and service availability.

## 2. Current controls

| Area | Control |
| --- | --- |
| Authentication | Auth Service issues signed JWT access tokens and rotating refresh tokens. |
| Passwords | BCrypt hashing. |
| Authorization | OAuth2 Resource Servers; `userId` ownership and `ROLE_ADMIN` routes. |
| Logout | JWT `jti` blacklist and refresh-token revocation. |
| Provider ingress | Stripe webhook signature verification and event deduplication. |
| Data isolation | Per-service databases; no cross-service foreign keys. |
| Secrets | External runtime configuration; secrets are not committed. |
| Monitoring | Trace-aware logs, metrics, health endpoints, and payment alerts. |

## 3. Threat register

| ID | Threat | Current mitigation | Required next control |
| --- | --- | --- | --- |
| SEC-01 | Credential theft | BCrypt; TLS deployment requirement. | MFA, rate limits, credential-stuffing detection. |
| SEC-02 | Stolen access token | Signature/expiry validation; Auth blacklist. | Enforce revocation/status/version at Gateway/resource services. |
| SEC-03 | Refresh-token database leak | Token lifecycle/revocation. | Store refresh-token hashes; rotate and detect reuse. |
| SEC-04 | JWT signing-key loss/change | Runtime RSA/JWK generation. | Persistent rotation-capable key store and JWKS rotation policy. |
| SEC-05 | IDOR/customer data exposure | JWT `userId` ownership checks. | Automated authorization tests for every owned resource. |
| SEC-06 | Admin privilege abuse | Admin-only routes. | Least privilege, audit trail, MFA, just-in-time elevation. |
| SEC-07 | Fake/replayed webhook | Signature verification and provider event uniqueness. | Replay window, provider IP policy, reconciliation queue. |
| SEC-08 | Kafka/event spoofing | Internal network assumption/idempotency. | TLS/SASL, topic ACLs, schema validation, service identities. |
| SEC-09 | gRPC misuse | Internal transport and error mapping. | mTLS/service auth, deadlines, authorization interceptor. |
| SEC-10 | DoS/rate abuse | Gateway capability exists. | Activate route limits, WAF, payload limits, circuit breakers. |
| SEC-11 | Sensitive log exposure | Structured log guidance. | Redaction tests, access control, retention policy. |

## 4. Security requirements

1. Enforce HTTPS externally and encrypted production transport where supported.
2. Use managed secret storage with rotation and least-privilege workload access.
3. Do not log passwords, access/refresh tokens, sensitive raw webhook payloads, or payment-provider secrets.
4. Restrict Actuator, OpenAPI, Config refresh, and database administration endpoints to trusted identities/networks.
5. Run dependency, container, static-code, and secret scans in CI; patch severe vulnerabilities under a defined SLA.
6. Audit administrator changes, refunds, privileged status transitions, and security-relevant authentication events.
7. Test authorization, webhook verification, rate limits, and token revocation before production releases.

## 5. Security incident response

Rotate exposed secrets/keys, revoke affected sessions, preserve relevant audit
logs, isolate suspicious workloads, and assess webhook/event integrity. Use the
operations runbook for recovery, then document root cause and prevention.
