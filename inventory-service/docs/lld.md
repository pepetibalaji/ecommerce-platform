# Low-Level Design

`InventoryService` locks inventory rows for mutations. `InventoryReservation` records stable
reservation IDs and states RESERVED, RELEASED, or DEDUCTED, making retries safe. Seller APIs call
`ProductOwnershipVerifier`; product-created events use the unique product ID to provision once.
