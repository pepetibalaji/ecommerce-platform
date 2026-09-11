# Product Service low-level design

## Request, query and mapping

ProductController maps /api/v1, enforces roles and derives JWT userId. Managed owner checks permit administrative support while concealing cross-seller product ownership with 404. Bulk creation accepts 1..100 non-null validated requests and is seller-only.

ProductService validates query lengths, independent price bounds, supported sorts and safe paging. All public combinations use ProductRepositoryCustom's single query implementation, including one-sided bounds. ProductRepositoryImpl applies case-insensitive collation to exact category/brand filters, escaped literal substring matching to q, inclusive numeric price conditions and active visibility.

Relevance uses a Mongo aggregation with tiered exact/prefix/substring name, brand and description ranking. Other orders use field sorts with compatible indexes where available; every order ends in ID ascending. Count and data queries have server execution budgets. Facet aggregation uses the same case-insensitive collation so count buckets match their filters.

ProductMapper returns UTC timestamps, ISO currency, legacy active visibility and empty image arrays. CataloguePageResponse exposes explicit fields without relying on Spring Data's internal JSON shape. Name/category/brand are trimmed on writes; blank optional labels become null. Description remains plain text.

## Transactions and revisions

Each public mutation service entry point is transactional. Creation, full update, activation changes and archival save a product revision and call ProductOutboxService.enqueue, which requires an existing transaction. Bulk uses one transaction across all product/event inserts. An enqueue failure rolls back prior writes in that transaction.

Spring Data's @Version guards concurrent updates. The event productVersion is persisted revision + 1. Startup initializes missing/null legacy revisions to zero and backfills missing/null currencies to USD. Mutation snapshots include their own catalogue state rather than rereading a later product during normal delivery.

## Worker and recovery

The outbox claims one due pending or expired processing record atomically, increments attempts and sets a unique lease token. Success/failure updates require the same token, preventing an expired worker from completing another worker's claim. Broker confirmation precedes PUBLISHED state. A crash between confirmation and state update can duplicate an event, so delivery is at least once.

Retries use exponential backoff and terminal DEAD records. Administrative replay resets dead delivery state. Reconciliation scans products in bounded ID-cursor batches and enqueues current product.reconciled snapshots without changing revisions. Legacy payloadless outbox records are repaired at delivery. Version-aware consumers ignore stale/duplicate snapshots and recover missing rows without resetting stock.

See [API](api.md), [schema](schema.md) and [lifecycle operations](lifecycle-operations.md) for limits, fields, lease timing, metrics and exact operator commands.
