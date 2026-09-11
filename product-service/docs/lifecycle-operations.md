# Catalogue lifecycle delivery and deployment

## Transaction boundary and schema

Every create, bulk create, descriptive update, deactivate, reactivate, and archive executes inside a MongoDB transaction. The product save and immutable event snapshot either both commit or both roll back. Bulk failure rolls back the whole submitted batch. `ProductOutboxService.enqueue` requires an existing transaction; publishing is never inside that transaction.

MongoDB **must be a replica set or sharded cluster**. Standalone servers do not support this transaction boundary. The local Compose MongoDB now runs the single-node `pepekart-rs` replica set and initializes it through its health check. Existing volumes are retained. For host-based local services use:

```text
PRODUCT_MONGODB_URI=mongodb://localhost:27017/product_db?replicaSet=pepekart-rs&directConnection=true
```

For a service inside Compose replace `localhost` with `mongodb` and keep `directConnection=true`. Production must use its managed replica-set/sharded URI and TLS credentials. Do not use the local single-node configuration as a production high-availability topology.

Startup creates the outbox indexes and initializes missing/null optimistic revisions on legacy products to zero. New records use Spring Data's optimistic revision; the published product version is revision + 1. A concurrent outdated update fails, rather than silently overwriting a newer snapshot. The old direct `ProductEventPublisher` has been removed.

## Event contract and consumer recovery

Topic: `product.lifecycle.v1`; key: product UUID. The contract lives in `common-events` as `ProductLifecycleEvent`, schema version 1. It includes event/product/seller IDs, UTC event time, event type, monotonically increasing product version, active flag, name, description, category, brand, price/currency, image URLs, and acting user ID when a JWT is available.

Types: `product.created`, `product.updated`, `product.deactivated`, `product.reactivated`, `product.archived`, and recovery snapshot `product.reconciled`. Archival retains the product document and history as `active=false`; eligible sellers' products can be restored explicitly. No public or management operation permanently purges historical records.

Inventory consumes full snapshots and atomically upserts only when the incoming version exceeds the stored version. Duplicate or reordered events do not reset stock. A later update or reconciliation snapshot can provision a product whose create event was missed. Archived/deactivated products cannot obtain new reservations; existing reservations can finish or release. Search consumers should apply the same product-version rule and use reconciliation to rebuild; this repository does not deploy a separate Search service.

The publisher claims at most the configured batch per tick. Claims use a 60-second lease with a unique token; acknowledgement and failure writes require ownership of that exact lease. Kafka sends time out after 15 seconds. Retries use exponential delay capped at one hour, and default to ten attempts before dead-letter state. Delivery is at least once: a crash after Kafka acknowledgement and before marking published may repeat the same immutable event ID.

Pre-upgrade outbox rows lacking a snapshot are converted to explicit `product.reconciled` snapshots at delivery. Their event IDs remain stable across retries. Published rows are retained for operational inspection; no automatic destructive retention is configured.

## Operator actions

Both endpoints require ADMIN and are available with confirmation in the admin catalogue UI.

```http
POST /api/v1/admin/products/outbox/replay-dead-letters
```

Returns 204 and resets terminal delivery attempts. Fix the underlying broker/configuration problem before replaying.

```http
POST /api/v1/admin/products/outbox/reconcile?size=100
```

Returns `{ "enqueued": 100, "nextAfterId": "uuid-or-null" }`. Supply the returned cursor as `afterId` on the next request; stop when it is null. Batches enqueue current snapshots, including inactive products, without altering their versions or stock. Repeating a batch is safe for version-aware consumers. Run a full pass after deploying the Inventory lifecycle migration, following consumer-data restoration, or after repairing missed deliveries. If catalogue writes overlap a scan, run another pass; regular mutations still deliver normally.

Metrics exposed through Micrometer/Prometheus: `product.outbox.pending`, `product.outbox.dead`, `product.outbox.oldest.seconds`, `product.outbox.delivered`, `product.outbox.failures`, `product.outbox.replayed` (Prometheus normalizes dots to underscores). Alert on nonzero dead count and increasing oldest pending age. Reconciliation repairs downstream gaps; replay repairs producer delivery failures.

## Configuration and rollout

Product dev/stage/prod configuration is maintained in `C:\e-com\ecommerce-config-repo`. Required settings:

- `PRODUCT_MONGODB_URI`: transaction-capable deployment.
- `AUTH_SERVICE_INTERNAL_URL` and the same `AUTH_INTERNAL_SERVICE_TOKEN` in Auth and Product. The internal token must come from deployment secrets. Stage/prod fail startup if missing/unresolved. Product uses 2-second connect and 3-second read limits and never forwards the credential through redirects.
- `PRODUCT_IMAGE_ALLOWED_HOSTS`: approved exact HTTPS storage/CDN hostnames. Stage/prod do not accept arbitrary hosts.
- `KAFKA_BOOTSTRAP_SERVERS`: shared broker, plus the environment's transport credentials.
- `PRODUCT_OUTBOX_BATCH_SIZE` (100), `PRODUCT_OUTBOX_MAX_ATTEMPTS` (10), `PRODUCT_OUTBOX_RETRY_DELAY_SECONDS` (5), `PRODUCT_OUTBOX_POLL_DELAY_MS` (1000), `PRODUCT_QUERY_TIMEOUT_MS` (2000).

Provision topics before starting the services. `scripts/create-kafka-topics.sh` includes `product.lifecycle.v1`, `product.lifecycle.v1-retry-1000`, `product.lifecycle.v1-retry-2000`, `product.lifecycle.v1-retry-4000`, and `product.lifecycle.v1-dlq`. The retry names match Inventory's four-attempt, 1/2/4-second backoff configuration and were verified in the Kafka integration test's listener subscriptions. Pre-provisioning these topics allows runtime service identities to operate without topic-creation permission. The local script uses three partitions and replication factor one; production provisioning must use the managed cluster's replication and authentication policies.

Apply Inventory's Flyway V4, deploy the shared contract and Inventory consumer before Product switches publication, and run reconciliation. The legacy Inventory `product-created` consumer is retained for rolling upgrade compatibility. No service deployment or production data mutation is performed by building this change.

## Acceptance checks

Run from the platform root with Docker available:

```powershell
mvn -pl product-service,inventory-service,auth-service,gateway-service -am verify
```

Product tests cover transactional rollback, bulk rollback, all lifecycle types over Kafka, retries/dead letters, fenced leases, reconciliation, real database public HTTP queries, inactive hiding, filter/relevance/pagination combinations, image/currency validation and Mongo index execution plans. Inventory tests exercise real Kafka/PostgreSQL, duplicate/stale delivery and preservation of stock. Auth tests exercise the actual internal security chain and persisted seller eligibility; Gateway tests exercise anonymous catalogue access and protected mutations. Dedicated container acceptance classes require Docker and must not be reported as passed when skipped.

For the real four-service HTTP/messaging acceptance run, package the services and run `./scripts/tests/catalogue-e2e.ps1`. It loads the adjacent config repository's Gateway routes and uses only isolated disposable dependencies. See [harness instructions](../../scripts/tests/README.md) and [recorded verification results](verification.md). Frontend regression checks are `npm --prefix frontend test` and `npm --prefix frontend run build`.
