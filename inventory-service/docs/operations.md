# Inventory Service Operations

Inventory consumes `product-created` as `inventory-product-provisioner`, creates one row keyed by
`product_id`, and sets both stock counters to zero. Duplicate events are successful no-ops because
`product_id` is unique. Failures retry four times with exponential backoff, then go to
`product-created-dlt`. Monitor consumer lag, DLT records, and `inventory_product_created_events_total`.
