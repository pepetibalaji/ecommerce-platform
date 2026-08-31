# Data Model

`orders` contains user, totals, currency, status, payment outcome fields, and shipping snapshot.
`order_items` contains product ID, immutable product name/price, seller ID, quantity, and inventory
reservation ID. Other tables store processed payment events and inventory-release outbox state.
V7 adds seller IDs; V8 adds product-name snapshots.
