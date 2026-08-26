# Data Privacy and Retention Policy

**Status:** Baseline policy to finalize with legal/compliance review  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Purpose

Define minimum handling rules for personal data, authentication data,
payment-related metadata, operational data, and deletion/retention workflows.
This is a technical baseline, not legal advice. Retention durations and regional
obligations require legal/compliance approval before production.

## 2. Data classification

| Classification | Examples | Handling |
| --- | --- | --- |
| Restricted | Password hashes, refresh tokens, JWTs, provider secrets, webhook signatures. | Never log/expose; encrypt transport/storage; strict access and rotation. |
| Personal data | Name, email, phone, shipping address, linked user/order IDs. | Minimize, access control, audit, redact logs, retain by policy. |
| Financial metadata | Payment/provider IDs, amounts, refunds, failure reasons. | Do not store card data; restrict access and retain only as required. |
| Operational | Trace/event IDs, metrics, service logs. | Avoid sensitive payloads; control access/retention. |
| Public/business | Product name, description, category, brand, public price. | Standard integrity and availability controls. |

## 3. Current data locations

| Data | Owner/location |
| --- | --- |
| Identity/profile | Auth `users` PostgreSQL table. |
| Refresh token | Auth `refresh_tokens` table; planned change to hash storage. |
| Cart | Redis `cart:{userId}`; seven-day inactivity TTL. |
| Shipping snapshot | Order `orders` table. |
| Payment metadata | Payment tables; card details must not be persisted. |
| Logs/traces/metrics | Local log files, Loki/Grafana Cloud, Tempo, Prometheus. |

## 4. Required handling rules

1. Collect fields only for account, order, payment, fulfilment, support,
   security, or legal obligations.
2. Encrypt data in transit and use managed encrypted storage/backups in production.
3. Apply role/ownership checks to all personal and financial records.
4. Redact secrets, tokens, password hashes, sensitive webhook payloads, and
   unnecessary personal data from logs, errors, traces, and support exports.
5. Use production data only in production; stage/local use generated or safely anonymized test data.
6. Audit administrator profile changes, refunds, and security actions.

## 5. Retention and deletion baseline

| Data | Current / proposed rule |
| --- | --- |
| Cart | Expires seven days after the last write. |
| Access-token blacklist | Expires no later than its access token. |
| Refresh tokens | Remove at expiry/revocation plus approved security-audit period. |
| Soft-deleted users | Anonymize/delete personal fields after approved legal, fraud, and support period. |
| Orders/payments/refunds | Retain immutable transactions for approved finance/legal period; limit access. |
| Webhook/inbox/outbox | Retain through retry/reconciliation plus approved incident/audit period. |
| Logs/traces | Environment-specific retention; hosted-log guidance currently cites 14-day Grafana Cloud Free retention. |

Exact duration, legal holds, export, and erasure exceptions must be approved and
documented per jurisdiction.

## 6. Data subject and incident processes

Authenticated profile access/correction exists today. Before production, define
identity-verified export, deletion/anonymization, and consent processes. For a
suspected personal-data incident: contain access, preserve evidence, assess
scope, rotate credentials where needed, notify incident/legal owners, and follow
applicable notification requirements.

## 7. Implementation backlog

- Hash refresh tokens and persist/rotate signing keys.
- Add audit tables/events for privileged and sensitive access.
- Approve retention schedules and build cleanup/anonymization jobs.
- Add address/notification consent preferences with those services.
- Require privacy review for new schemas, events, AI, analytics, and third-party integrations.
