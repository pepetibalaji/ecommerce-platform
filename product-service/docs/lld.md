# Low-Level Design

`ProductController` exposes `/products`, `/admin/products`, and `/seller/products`. It delegates
to `ProductService`, which creates UUIDs/timestamps, performs seller ownership checks, and maps
documents using `ProductMapper`. Cross-seller access uses not-found semantics.

`ProductRepository` persists Mongo `Product` documents. `ProductEventPublisher` sends a Kafka
`ProductCreatedEvent` keyed by product ID after save. Publish failures increment
`product_created_event_publish_failures_total` for replay/reconciliation. `active=false` is the
catalog availability signal that prevents checkout.
