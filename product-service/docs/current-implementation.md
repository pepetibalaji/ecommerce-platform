# Product Service current implementation

## Implemented

* MongoDB catalog document with UUID identity, seller owner, price stored as Decimal128, descriptive fields, HTTPS image URLs, active status, and timestamps.
* Public product read/list APIs with paging, category filter, and paired min/max price filter.
* Admin create, bulk create, full update, and hard delete APIs.
* Seller create/list/update/delete APIs scoped from JWT `userId`; cross-seller mutations use not-found semantics.
* `product-created` Kafka publication after create, plus a publish-failure metric.
* Mongo index initialization, OpenAPI, OAuth resource server, Actuator, structured logs, Prometheus, and tracing dependencies.

## Important limitations

* Public catalog lists exclude inactive products, while direct reads may still return them so checkout can enforce availability explicitly.
* A one-sided `minPrice` or `maxPrice` query is ignored; range filtering requires both.
* Updates are full replacements for all descriptive fields and have no optimistic-lock/version protection.
* Creation persistence and Kafka send are not transactional. A product can exist without inventory provisioning; no outbox/retry/replay endpoint exists.
* Updates/deletes do not publish catalog lifecycle events.
* Product Service does not manage image binaries, price history, search, inventory, checkout, or purchase snapshots.

See [API and contracts](api.md) for integration details and [Events and operations](events-and-operations.md) for production behavior.
