# Product, inventory, and seller ownership workflows

## Order creation

`POST /api/v1/orders` accepts each item as `{ "productId": "...", "quantity": 1 }`.
Order Service reads `/api/v1/products/{productId}` before it checks or reserves stock. The
catalog response supplies the seller, product name, and unit price; request prices are neither
accepted nor used. A missing, inactive, or incomplete catalog item fails the request before an
inventory reservation is made. A catalog transport failure also fails closed with a retryable
client error, so a stale or guessed price can never become an order.

`order_items.price` and `order_items.product_name` are immutable purchase-time snapshots.
`seller_id` is likewise captured at checkout, which allows seller-order queries to return only
the matching line items from a multi-seller order.

## Product-created inventory provisioning

After Product Service saves a product it emits `product-created` with this version-1 payload:

```json
{ "eventId": "uuid", "eventType": "PRODUCT_CREATED", "productId": "uuid", "sellerId": "uuid" }
```

Inventory Service consumes this event in `inventory-product-provisioner` and creates one record
with `availableStock = 0` and the event's seller owner. The unique `inventory.product_id` key
makes duplicate delivery a successful no-op. Publish failures are logged with `productId` and
counted by `product_created_event_publish_failures_total`; successful/duplicate consumption is
counted by `inventory_product_created_events_total`.

Kafka consumer failures are intentionally rethrown, allowing the configured Kafka retry/DLT
policy to retry and surface them. Operations should alert on publish failures, consumer lag, and
the absence of an inventory row for a newly created product. Reconciliation is safe: replay the
`product-created` event or invoke the idempotent inventory provisioning path for the product ID;
never delete the product or create a second stock row.

## Seller isolation

Products and inventories carry `sellerId`. `/api/v1/seller/products/**`,
`/api/v1/seller/inventory/**`, and `/api/v1/seller/orders/**` derive the owner from JWT `userId`.
Seller checks are performed in service code; ownership mismatches return not-found. Admin routes
remain platform-wide. Admin product creation must explicitly provide the target `sellerId`.

## Rollout of existing data

The SQL migrations deliberately leave legacy `seller_id` values nullable because there is no
safe way to infer an owner from historical data. Before enabling seller access in production,
an administrator must backfill product owners from the seller-of-record, then set the matching
inventory owner from the product. Products with no authoritative seller-of-record must remain
admin-only and be reconciled manually; they must not be exposed through seller endpoints.
