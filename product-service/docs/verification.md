# Catalogue hardening verification

Verified on 2026-09-10 on branch `feature/product-catalogue-hardening`.

| Check | Result |
| --- | --- |
| Product tests | 100 passed, zero failures/errors/skips. |
| Auth tests | 49 passed, zero failures/errors/skips. |
| Inventory tests | 24 passed, zero failures/errors/skips. |
| Gateway tests | 12 passed, zero failures/errors/skips. |
| Frontend regression tests (`npm test`) | 56 passed, zero failures/skips. |
| Real cross-service HTTP/messaging acceptance | 21 checks passed. |
| Four service jars and shared dependencies | Maven package succeeded. |
| Frontend production bundle | TypeScript and Vite build succeeded. |
| Configuration checks | Compose validation and both repositories' diff checks passed. |

The backend suites include real MongoDB replica-set transactions, Kafka, and PostgreSQL. They verify whole-batch rollback, immutable event snapshots, retry/backoff/dead-letter replay, lease fencing, legacy migration, duplicate/stale consumer handling, indexed queries, and security/error contracts.

The four-service acceptance harness uses actual Auth-issued JWTs, secured Product-to-Auth HTTP eligibility calls, the dev Gateway routes from `C:\e-com\ecommerce-config-repo`, and real Product-to-Kafka-to-Inventory delivery. It passed anonymous discovery/filter/sort/pagination, seller ownership and bulk creation, all five lifecycle types, archival visibility, stock preservation, reconstruction of a missing Inventory fixture through reconciliation, suspended-seller rejection, and 503 fail-closed behavior after Auth stops.

Successful local run: `e8fe39e3634a49159a991aaa79e23bb9`; [machine-readable results](../../target/catalogue-e2e/e8fe39e3634a49159a991aaa79e23bb9/result.json). Test logs live under the same directory and are build artifacts, not source-controlled production data. See [repeatable harness instructions](../../scripts/tests/README.md).

Frontend tests cover API and server-rendered UI contracts, including authenticated management reads, seller bulk import, archive/reactivation, recovery requests, pagination, and validation messages. They are not browser-driven visual or usability tests.

## Deployment boundary

Implementation and local acceptance verification are complete; this is not a production deployment. Deploy both repositories' changes using the [rollout instructions](lifecycle-operations.md): transaction-capable MongoDB, matching injected Auth/Product service credentials, lifecycle topics, Inventory Flyway V4, consumer-first rollout, then reconciliation. Existing application data was not used for the acceptance run.
