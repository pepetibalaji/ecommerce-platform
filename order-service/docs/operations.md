# Order Service Operations

Before reserving stock, Order Service requests the current catalog product. It rejects missing or
inactive products and fails closed if the catalog cannot be reached within configured timeouts.
Each `order_items` row stores immutable `product_name`, `price`, and `seller_id` snapshots.

Stock is reserved through Inventory gRPC with a stable reservation ID. Failed creation compensates
by releasing every attempted reservation. Order-created events publish to `order-created`; payment
outcomes consume `payment-success`, `payment-failed`, and `payment-refund-completed`.
