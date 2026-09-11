# Product Service events and operations

The maintained contract and recovery runbook are in [Catalogue lifecycle delivery and deployment](lifecycle-operations.md).

Product publishes `ProductLifecycleEvent` snapshots on `product.lifecycle.v1`, keyed by product UUID. Product mutation and outbox insertion share one Mongo transaction. A separate worker delivers with retry/backoff, expiring claims and fenced acknowledgements.

Lifecycle types are product.created, product.updated, product.deactivated, product.reactivated, product.archived and recovery snapshot product.reconciled. Each event includes stable event identity, seller/product IDs, UTC occurrence time, schema version, monotonic product version, current catalogue state and actor where available.

Consumers must apply only newer product versions and tolerate repeated event IDs. Inventory can recover a missed create from a later snapshot and never resets stock on duplicates. Its legacy product-created consumer remains for rolling-upgrade compatibility; Product no longer directly publishes that old topic.

Use the ADMIN replay endpoint to retry dead outbox deliveries after fixing the cause. Use bounded cursor reconciliation to republish current state after consumer restoration or missed broker history. Exact routes, response formats, metrics, configuration, Mongo rollout requirements and tests are documented in [lifecycle operations](lifecycle-operations.md); browser contracts are in [API](api.md).
