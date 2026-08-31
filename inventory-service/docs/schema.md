# Data Model

`inventory` has UUID ID, unique `product_id`, nullable legacy `seller_id`, available/reserved stock,
and update time. `inventory_reservations` stores reservation ID, product, quantity, and lifecycle.
V2 adds reservations; V3 adds seller ownership/index. Backfill legacy seller IDs before seller rollout.
