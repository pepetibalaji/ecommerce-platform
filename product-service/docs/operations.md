# Product Service operations

Use [Catalogue lifecycle delivery and deployment](lifecycle-operations.md) as the maintained deployment, monitoring and recovery runbook. It covers transaction-capable MongoDB, legacy migration, secured Auth eligibility configuration, lifecycle delivery, leases/retries/dead records, reconciliation, metrics, topic rollout and integration acceptance commands.

Use [API and contracts](api.md) for routes, request/query validation, public search limits and browser error handling, and [Data model](schema.md) for product/outbox fields and indexes.

Production deployment and operator replay/reconciliation are explicit operational actions; local builds and tests do not perform them.
