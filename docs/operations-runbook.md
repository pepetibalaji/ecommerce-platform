# Operations Runbook

**Status:** Current operational response baseline  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. First-response checklist

1. Identify environment, affected service, start time, correlation/order/payment/event IDs, and user impact.
2. Check Gateway/service `/actuator/health`, Prometheus targets, Grafana dashboards, Tempo traces, and structured logs.
3. Check PostgreSQL/Redis/Kafka health and Kafka consumer lag before restarting components.
4. Preserve logs and relevant event/payload metadata; avoid exposing tokens or payment secrets.
5. Escalate suspected security or payment-provider incidents immediately.

## 2. Alert response

| Alert/symptom | Diagnose | Safe response |
| --- | --- | --- |
| Payment outcome DLQ | Inspect event schema, order existence/state, consumer logs, database availability. | Fix cause; replay only after validating idempotency and expected order state. |
| Outcome retry spike/lag | Check Order availability, partitions, broker health, deserialization, database locks. | Scale/restart consumer only after cause check; monitor lag reduction. |
| Inventory release retry spike | Inspect outbox rows, Inventory health, gRPC errors, reservation state. | Restore dependency; let idempotent worker retry. Do not manually alter stock without reconciliation. |
| Webhook failures | Check signature secret, provider event ID, route/TLS, matching payment attempt. | Correct config/route; reconcile unmatched verified events. |
| Auth failures | Check issuer/JWK reachability, clocks, token expiry, user status/version. | Restore JWK/config; revoke/rotate if key compromise is suspected. |

## 3. Kafka DLQ replay procedure

1. Copy and retain the DLQ record, topic/partition/offset/key, event ID, and trace ID.
2. Confirm root cause is fixed and event schema is understood.
3. Read current Order/Payment state and verify intended transition remains valid.
4. Replay through approved tooling to the original topic with original key/event ID.
5. Confirm consumer processing, expected state, and no duplicate release/payment effect.
6. Record operator, reason, before/after state, and result.

## 4. Inventory release recovery

Order release commands are durable in `order_inventory_release_outbox`. Inspect
pending command, attempts, last error, reservation ID, product, and quantity.
Restore Inventory/gRPC connectivity first. The worker performs an idempotent
release and marks work complete. Manual database status changes or stock edits
require an approved reconciliation procedure and audit record.

## 5. Payment and provider recovery

Provider webhook is the payment authority. Never mark orders confirmed from a
browser return alone. For a missing/failed webhook, verify provider dashboard
event, signature configuration, route access, payment attempt mapping, and
deduplication record. Use a verified provider event/reconciliation process; do
not hand-publish a success event in production.

## 6. Backup, restore, and incident closeout

Production requires tested PostgreSQL backups/point-in-time recovery, Redis
recovery policy, Kafka retention/replication policy, and restore exercises.
After recovery, validate migrations, service health, consumer lag, outbox/DLQ,
and representative order/payment flows. Close incidents with impact, timeline,
root cause, fixes, prevention actions, and linked evidence.
