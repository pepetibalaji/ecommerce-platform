# Product Service API and contracts

Base path: `/api/v1`. Bodies are JSON; product and seller identifiers are UUIDs. Public catalogue reads are anonymous through Gateway and Product Service. Management routes require a bearer JWT and the stated role.

## Product data

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "sellerId": "22222222-2222-2222-2222-222222222222",
  "active": true,
  "name": "Phone",
  "description": "Plain-text product description",
  "price": 100.25,
  "currency": "USD",
  "category": "Mobile",
  "brand": "Acme",
  "imageUrls": ["https://cdn.example.com/products/phone.jpg"],
  "createdAt": "2026-09-10T10:15:30Z",
  "updatedAt": "2026-09-10T10:15:30Z"
}
```

Timestamps are UTC `Instant` values serialized with `Z`. Missing/null legacy active flags are treated as active; explicit `false` hides the item publicly. Currency defaults to `USD` when omitted for compatibility.

Create accepts the descriptive fields above, without server-managed ID, ownership, active status, or timestamps. Update fully replaces descriptive fields and accepts optional `active`; omitting it preserves its value. Name/category/brand have surrounding whitespace trimmed, and blank category/brand become null. Omitted/null optional strings become null; omitted/null `imageUrls` becomes `[]`.

| Field | Validation |
| --- | --- |
| `name` | Required, nonblank, at most 200 characters. |
| `description` | Optional plain text, at most 10,000 characters. |
| `price` | Required, strictly positive decimal; at most 18 integer digits and 4 decimal places, stored exactly as BSON Decimal128. Extra precision is rejected with 400, never rounded. |
| `currency` | Uppercase ISO 4217 currency recognized by the deployed JVM with monetary fraction digits, e.g. USD, INR, EUR, JPY. Invented codes, metal codes, XXX, explicit null and blank values are rejected. |
| `category`, `brand` | Optional; each at most 100 characters. |
| `imageUrls` | At most 10 nonblank HTTPS URLs, each at most 2,048 characters, on an approved image host. Credentials, fragments, and non-443 explicit ports are rejected. |

Descriptions are plain text: clients render escaped text, never HTML. Product does not implement rich-text sanitization or automatic moderation. Administrators moderate by updating/deactivating/archiving products; sellers remain responsible for accuracy and image rights.

Image hosts use `product.images.allowed-hosts` / `PRODUCT_IMAGE_ALLOWED_HOSTS`. Arbitrary external hosts are rejected. Product stores references and does not fetch images or infer their ownership. Sellers should upload to approved storage using seller-scoped ownership rules before submitting URLs. Frontend placeholders handle absent/failed images. Storage removal does not remove product/order history; replace catalogue URLs when retiring assets. Product does not automatically probe image availability.

## Public catalogue

| Method and path | Query parameters | Result |
| --- | --- | --- |
| `GET /products` | q, category, brand, minPrice, maxPrice, sort, page, size | Stable page below. |
| `GET /products/facets` | None | Global active category/brand counts and numeric price range. |
| `GET /products/{productId}` | None | Active product or 404. |

All filters work independently and together. Category/brand use trimmed case-insensitive exact matches. Search is a trimmed case-insensitive literal substring across name, brand and description: `.*` matches that text, not a regex. Blank strings mean no filter. Search allows at most 200 characters; category/brand queries at most 100.

Price bounds are inclusive, nonnegative, and independent; `minPrice > maxPrice` returns 400. Page defaults to 0 and must be nonnegative; size defaults to 10 and must be 1..100.
Price query bounds outside lossless BSON Decimal128 precision or exponent range are also rejected with 400 rather than rounded.

| Sort | Ordering |
| --- | --- |
| `newest` (default) | Creation time descending. |
| `price_asc`, `price_desc` | Numeric price ascending/descending. |
| `name_asc`, `name_desc` | Case-insensitive name ascending/descending. |
| `relevance` | Exact name > name prefix > name substring > exact brand > brand substring > description substring. Each product receives its best matching tier. Without nonblank q, uses newest. |

Every order ends with product ID ascending as a stable tiebreaker; unknown sort values return 400. Pagination is deterministic for an unchanged catalogue; concurrent edits can change membership. Price filters/sorts/facets compare numeric amounts without exchange-rate conversion and are not cross-currency price comparisons.

Example: `GET /api/v1/products?q=phone&category=mobile&brand=acme&minPrice=50&maxPrice=150&sort=relevance&page=0&size=10`.

The explicit page DTO is independent of Spring Data serialization:

```json
{
  "content": [],
  "number": 0,
  "size": 10,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true,
  "empty": true,
  "numberOfElements": 0
}
```

Nonempty content entries follow the Product data example. Out-of-range pages preserve requested number/size and matching total count.

Facets are global active-catalogue counts, not filtered by the current search:

```json
{
  "categories": [{"name":"Mobile","count":42}],
  "brands": [{"name":"Acme","count":12}],
  "priceRange": {"min":99.00,"max":1999.00}
}
```

Empty catalogues have empty lists and null price bounds. Inactive products do not contribute.

### Indexes and query limits

Startup ensures collation-compatible indexes for active/newest, active/price, active/name, category/active/price and brand/active/price, including ID tiebreakers. Equality filters and name sorting use the same case-insensitive collation.

Ordinary B-tree indexes cannot fully serve literal substring matching or computed relevance. Mongo first filters candidates, then evaluates substring matches and ranks them. Broad searches may scan the active catalogue; this is not a full-text engine or a constant-time guarantee. Each count and data query has a server budget, `product.catalogue.query-timeout-ms` (default 2000 ms); timeouts return 503. For larger catalogues, use selective filters or a dedicated search read model.

`ProductRepositoryMongoIntegrationTest` includes a 1,000-row execution-plan regression. An unhinted case-insensitive category + literal search + price sort must use the selective index and examine at most 20 candidate documents for ten results. Other tests cover Decimal128 prices, independent/combined filters, escaping, relevance, pagination and inactive/legacy visibility. This checks query work, not a production latency SLA.

## Administration and sellers

Admin creation requires a sellerId UUID. Product calls Auth's secured seller-eligibility endpoint before accepting ownership. Unknown, inactive, deleted or otherwise ineligible sellers are rejected; dependency failure fails closed.

| Method and path | Role | Success |
| --- | --- | --- |
| `POST /admin/products?sellerId={sellerId}` | ADMIN | 201 product owned by the eligible supplied seller. |
| `PUT /admin/products/{productId}` | ADMIN | 200 updated product. |
| `GET /admin/products/{productId}` | ADMIN | 200 management product, including inactive. |
| `DELETE /admin/products/{productId}` | ADMIN | 204; archive and retain product. |
| `POST /admin/products/{productId}/deactivate` | ADMIN | 200 inactive product. |
| `POST /admin/products/{productId}/reactivate` | ADMIN | 200 active product. |
| `POST /admin/products/outbox/replay-dead-letters` | ADMIN | 204; dead records queued for retry. |
| `POST /admin/products/outbox/reconcile?size=100&afterId={id}` | ADMIN | 200 bounded cursor batch of current snapshots; response has enqueued and nextAfterId. Omit afterId for the first batch; stop when nextAfterId is null. Size is 1..100. |
| `POST /seller/products` | SELLER or ADMIN | 201 product owned by authenticated eligible seller identity. |
| `POST /seller/products/bulk` | SELLER | 201 product list; JSON array of 1..100 create requests. |
| `GET /seller/products?page=0&size=10` | SELLER or ADMIN | 200 stable page of authenticated identity's products, including inactive. |
| `GET /seller/products/{productId}` | SELLER or ADMIN | 200 owned management product, including inactive; ADMIN may support any seller. |
| `PUT /seller/products/{productId}` | SELLER or ADMIN | 200 owned product; ADMIN may support any seller. |
| `DELETE /seller/products/{productId}` | SELLER or ADMIN | 204 owned product archived; ADMIN may support any seller. |
| `POST /seller/products/{productId}/deactivate` | SELLER or ADMIN | 200 owned inactive product; ADMIN may support any seller. |
| `POST /seller/products/{productId}/reactivate` | SELLER or ADMIN | 200 owned active product; ADMIN may support any seller. |

Seller identity comes from JWT userId, never request ownership fields. Administration supports the multi-role JWT contract. Cross-seller mutations return 404. Bulk validation/persistence/outbox enqueue are one transaction, so a failure rolls back the entire batch.

Deactivation/reactivation/archival produce auditable lifecycle changes. DELETE never physically removes a product. No hard-purge endpoint exists; a future internal retention purge must separately prove that carts/orders/payments/analytics no longer require the record.

## Delivery and Product versus Inventory

Product owns descriptions, images, seller ownership and list price. Inventory owns stock and sellability. Product intentionally returns no stock count: visible/active does not promise in-stock. Checkout/order validation remains authoritative for final price and availability, including items previously added to carts. Deactivated/archived products disappear from public discovery and direct reads.

Product mutation and an immutable lifecycle snapshot commit in one Mongo transaction; deployment requires a replica set. Events on `product.lifecycle.v1` contain event ID, product ID, seller ID, UTC time, type, schema version, monotonic product version, catalogue state, and actor where available. Types include product.created, product.updated, product.deactivated, product.reactivated and product.archived; reconciliation can republish current state for recovery.

Delivery is at least once. Workers use expiring claim leases, acknowledge after Kafka confirmation, retry with backoff and retain exhausted records for operator replay. Consumers deduplicate and ignore older product versions; duplicate creation must not reset stock. Monitor pending/dead counts, oldest pending age, delivery failures and replays.

## Errors

Service errors contain timestamp, status, error, message and path; validation adds fieldErrors. Treat status/field keys as stable, not message wording. Gateway/security denials can occur before controllers. The shared error envelope currently uses an offset-free timestamp; this is separate from UTC product createdAt/updatedAt and event occurredAt.

```json
{
  "timestamp": "2026-09-10T10:15:30",
  "status": 400,
  "error": "Bad Request",
  "message": "Request validation failed",
  "path": "/api/v1/admin/products",
  "fieldErrors": {"currency":"Currency must be a supported uppercase ISO 4217 code"}
}
```

| Status | Meaning and client behavior |
| --- | --- |
| 400 | Invalid body/UUID/query/range/sort/currency/image or ineligible seller. Correct inputs. |
| 401 | Missing/invalid/expired management credentials. Refresh or sign in. |
| 403 | Missing role. Do not retry unchanged. |
| 404 | Missing/inactive public product or cross-seller mutation. Remove stale discovery links. |
| 409 | Concurrent mutation conflict. Reload before retrying. |
| 429 | Deployment/gateway throttling. Respect Retry-After when supplied. |
| 503 | Dependency unavailable or query timed out. Retry with backoff; narrow expensive searches. |
| 500 | Unexpected server failure. Generic response; exception details are logged internally. |

Product does not impose a separate fixed public-catalogue rate limit; 429 applies where deployment policy enables throttling.

OpenAPI: `/v3/api-docs`. Swagger UI: `/swagger-ui.html`. Includes anonymous catalogue security, queries/sorts, create/update examples, stable page metadata and errors.

See [lifecycle operations](lifecycle-operations.md) for deployment, migration, retries, monitoring, and reconciliation procedures.
