# Product Service current implementation

Implemented behavior:

- Anonymous catalogue list/detail/facets through Product and Gateway. Inactive products are excluded from public results and direct reads return 404.
- Validated literal case-insensitive search across name/brand/description; deterministic tiered relevance, newest/name/price sorts and stable ID tiebreakers.
- Independent and combined category/brand/minimum/maximum filters, validated paging, explicit stable page responses, case-insensitive facet grouping.
- Seller-scoped single/bulk creation, managed reads, updates, deactivate/reactivate and archival. Bulk creation belongs to SELLER; administrators manage platform support and explicit eligible seller ownership.
- Auth seller eligibility checks with secured internal credentials, bounded timeouts and fail-closed behavior.
- UTC product timestamps, ISO currency, lossless bounded price precision, text limits and approved HTTPS image validation.
- Mongo optimistic revisions and transactions covering every mutation plus an immutable outbox snapshot. Whole-batch rollback on bulk failure.
- Lifecycle delivery with claim leasing/fencing, retry backoff, dead-record replay, monitoring and cursor reconciliation. Inventory applies only newer snapshots and preserves stock/reservations.
- Generated OpenAPI examples, consistent error envelopes, security checks and real Mongo/Kafka/PostgreSQL integration acceptance coverage.

Operating boundaries:

- Product owns list price/catalogue data, not stock, checkout decisions, purchase snapshots or image storage.
- Description is plain text; no automatic moderation or rich-text sanitizer is provided.
- Literal substring search can scan selected candidates and is time-bounded. No dedicated Search service or production latency SLA is implied.
- Price filters/facet bounds compare amounts without exchange-rate conversion.
- No hard purge or automatic destructive outbox retention is configured.
- At-least-once delivery permits duplicates and reordering; consumer product-version checks provide convergence.
- Production requires transaction-capable MongoDB, operational Kafka, configured internal credentials and approval to deploy. Building/tests do not deploy services.

See the maintained [API contract](api.md) and [lifecycle operations](lifecycle-operations.md), including exact acceptance commands. A skipped container test is not integration verification.
