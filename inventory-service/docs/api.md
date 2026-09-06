# Inventory Service API and contracts

REST base path: `/api/v1`. All REST routes require a bearer JWT. Admin endpoints require `ADMIN`; seller endpoints accept `SELLER` or `ADMIN`. IDs are UUIDs.

## REST payloads

### Create inventory

```json
{ "productId": "11111111-1111-1111-1111-111111111111", "availableStock": 25 }
```

`productId` is required and `availableStock` is required and at least zero. A created row begins with `reservedStock: 0`.

### Update inventory

```json
{ "availableStock": 25 }
```

This replaces the available counter directly. It requires a non-negative value and does not reconcile or modify `reservedStock`; management clients must avoid violating business expectations around outstanding reservations.

### Inventory response

```json
{
  "productId": "11111111-1111-1111-1111-111111111111",
  "availableStock": 25,
  "reservedStock": 4
}
```

Seller ID, database row ID, and timestamps are intentionally not returned.

## REST endpoints

| Method and path | Access | Behavior | Success |
| --- | --- | --- | --- |
| `POST /admin/inventory` | ADMIN | Creates an inventory row for a product. This is a manual/legacy operation. | `200` inventory |
| `GET /admin/inventory/{productId}` | ADMIN | Reads any row. | `200` inventory |
| `PUT /admin/inventory/{productId}` | ADMIN | Directly replaces available stock for any row. | `200` inventory |
| `POST /seller/inventory` | SELLER, ADMIN | Non-admin seller must own the product according to Product Service; admin bypasses verification. | `200` inventory |
| `GET /seller/inventory/{productId}` | SELLER, ADMIN | Non-admin seller ownership is verified through Product Service before read. | `200` inventory |
| `PUT /seller/inventory/{productId}` | SELLER, ADMIN | Non-admin seller ownership is verified before direct available-stock update. | `200` inventory |

A seller create uses the generic creation path, which stores `seller_id` as null; seller ownership is enforced through Product Service rather than from the inventory row. Admin creation likewise stores null seller ID. Kafka provisioning is the only creation path that stores event `sellerId` in the inventory row.

## gRPC contract

The gRPC service is `InventoryService` (normally port `9091`). It is intended for trusted internal callers such as Order Service. The protobuf definition is [`inventory.proto`](../../common/common-proto/src/main/proto/inventory.proto).

| RPC | Request | Result |
| --- | --- | --- |
| `GetInventory` | `productId` | `InventoryDetails { productId, availableStock, reservedStock }` |
| `ReserveStock` | `productId`, positive `quantity`, optional `reservationId` | `{ success: true, message }` |
| `ReleaseStock` | same | `{ success: true, message }` |
| `DeductStock` | same | `{ success: true, message }` |

For every mutating RPC, callers should always provide a stable UUID `reservationId` and reuse it on retry. With an ID, reservation is created once; duplicate reserve while `RESERVED`, duplicate release after `RELEASED`, and duplicate deduct after `DEDUCTED` are no-ops. An omitted/blank ID invokes legacy quantity-only behavior, which mutates on every retry and is not idempotent.

State rules: only `RESERVED` can release or deduct. `RELEASED` cannot deduct; `DEDUCTED` cannot release; re-reserving an existing released/deducted ID fails. A reused ID must have the same product and quantity.

gRPC maps missing inventory to `NOT_FOUND` and invalid business preconditions (including insufficient stock) to `FAILED_PRECONDITION`.

## REST errors

| Condition | Status |
| --- | --- |
| Missing/invalid JWT | `401` |
| Insufficient role | `403` |
| Invalid body | `400` field-validation map |
| Existing inventory on create | `409` `ApiErrorResponse` |
| Missing inventory / non-owned product | `404` `ApiErrorResponse` |

OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
