# Inventory Service low-level design

## Components

| Layer | Types | Responsibility |
| --- | --- | --- |
| REST | `InventoryController`, `SellerInventoryController` | Admin/seller management endpoints and JWT identity extraction. |
| Application | `InventoryService` | Counter mutations, ledger state, transactions, validation. |
| Persistence | JPA repositories | Product lookup and reservation lookup with `PESSIMISTIC_WRITE`. |
| gRPC | `InventoryGrpcService`, exception advice | Maps protobuf calls to safe or legacy service overloads. |
| Kafka | `ProductCreatedConsumer` | Idempotently provisions zero stock from Product Service. |
| Ownership | `HttpProductOwnershipVerifier` | Calls Product Service `GET /api/v1/products/{id}` for non-admin seller routes. |

## Transaction and state behavior

All mutations are `@Transactional`. `findByProductIdForUpdate` locks the inventory row. Reservation-aware methods then lock an existing reservation row by ID, check product/quantity consistency, and update the counter and reservation atomically.

```text
RESERVED -- release --> RELEASED
RESERVED -- deduct  --> DEDUCTED
RELEASED / DEDUCTED -- reserve --> rejected
RELEASED -- deduct --> rejected
DEDUCTED -- release --> rejected
```

Reserve: `available -= quantity`, `reserved += quantity` after availability check. Release reverses it. Deduct: `reserved -= quantity`; available remains unchanged because it was lowered at reservation time.

There is no separate stock-adjustment audit record, optimistic version, or database check preventing a direct REST available-stock update from being inconsistent with reserved stock. Concurrent normal mutations are serialized per inventory product by the row lock.

## Seller verifier and Kafka consumer

The verifier uses `RestClient` against configurable Product Service URL and compares returned `sellerId`; service/network failures are not translated to a dedicated inventory error. The Kafka listener group defaults to `inventory-product-provisioner`, retries up to four attempts with 1s exponential backoff multiplier 2, then publishes to `product-created-dlq` (the generated suffix from `-dlq`). On success it increments `inventory_product_created_events_total`.
