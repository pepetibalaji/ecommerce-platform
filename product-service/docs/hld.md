# Product Service high-level design

Product is the catalogue and list-price boundary. It owns identity, eligible seller ownership, descriptive content, currency, active status, approved image references and UTC audit timestamps. Inventory owns stock/reservations. Checkout/order validation determines final price and availability.

## Components

| Component | Responsibility |
| --- | --- |
| Gateway / resource-server security | Anonymous catalogue reads; authenticated seller/admin management; current multi-role JWT support. |
| ProductController | Stable browser-facing requests/responses, body validation and authenticated identity. |
| ProductService | Query validation, ownership, normalized product content and transactional lifecycle orchestration. |
| SellerEligibilityClient / Auth | Secured eligible-seller lookup; unavailable Auth fails closed. |
| ProductRepository / MongoTemplate | Authoritative products, indexed filtering/ranking/facets and optimistic concurrency. |
| Mongo transactions / ProductOutboxService | Atomic product plus immutable event persistence, durable delivery state and worker claims. |
| Kafka / Inventory consumer | Versioned lifecycle snapshots, idempotent metadata updates and downstream recovery. |
| Reconciliation / metrics | Bounded current-state republishing and delivery failure visibility. |

## Mutation and delivery flow

1. Authorize role, validate body and derive seller identity from JWT or an explicit admin seller query.
2. Verify eligible ownership for creation/reactivation.
3. In one Mongo transaction, persist the product revision and immutable lifecycle event snapshot.
4. Return the committed product or archival response.
5. A separate worker leases pending events, publishes keyed lifecycle snapshots and marks them delivered only after Kafka acknowledgement.
6. Inventory accepts newer product versions, preserving stock. Retries, duplicates and reordered events converge to current metadata.

Bulk creation uses one transaction for every product/event in the submitted batch. Deactivation and archival retain products and hide them publicly; they prevent new inventory reservations once propagated. Existing carts/orders require authoritative checkout validation.

## Discovery and management

Public browsing combines search, category, brand and one/two-sided price bounds with documented deterministic sorting and stable pages. Public inactive detail returns 404. Managed reads include inactive products; cross-seller access is 404 unless authorized administration applies.

Search uses bounded literal substring evaluation with indexed candidate filters. Facets group case variants consistently with those filters. Product does not host an independent search engine, image uploads, stock counters, or immutable purchase history.

See [API](api.md) for exact contracts, [schema](schema.md) for persistence, and [lifecycle operations](lifecycle-operations.md) for deployment and recovery.
