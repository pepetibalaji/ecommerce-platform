# Inventory Service API and contracts

REST base path: `/api/v1`. All REST routes require a bearer JWT. Admin endpoints require `ADMIN`; seller endpoints accept `SELLER` or `ADMIN`. IDs are UUIDs.

## REST payloads

### Create inventory

```json
{ "productId": "11111111-1111-1111-1111-111111111111", "availableStock": 25 }
```

`productId` is required and `availableStock` is required and at least zero. A created row begins with `reservedStock: 0`.

### Stock adjustment

```json
{ "adjustment": 25, "reason": "STOCK_RECEIVED", "referenceId": "optional UUID" }
```

`adjustment` must be non-zero. Supported reasons are `STOCK_RECEIVED`, `STOCK_CORRECTION`, `DAMAGE`, `RETURN`, and `MANUAL_RECONCILIATION`. The new available counter cannot become negative; every accepted change is recorded in the immutable adjustment ledger.

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
| `POST /admin/inventory/{productId}/adjustments` | ADMIN | Applies an audited delta without modifying reserved stock. | `200` inventory |
| `POST /seller/inventory` | SELLER, ADMIN | Non-admin seller must own the product according to Product Service; admin bypasses verification. | `200` inventory |
| `GET /seller/inventory/{productId}` | SELLER, ADMIN | Non-admin seller ownership is verified through Product Service before read. | `200` inventory |
| `POST /seller/inventory/{productId}/adjustments` | SELLER, ADMIN | Applies an audited delta after non-admin ownership verification. | `200` inventory |

A seller create persists the verified seller ID. Admin creation has no seller identity unless provisioned from product lifecycle events.

## gRPC contract

The gRPC service is `InventoryService` (normally port `9091`). It is intended for trusted internal callers such as Order Service. The protobuf definition is [`inventory.proto`](../../common/common-proto/src/main/proto/inventory.proto).

| RPC | Request | Result |
| --- | --- | --- |
| `GetInventory` | `productId` | `InventoryDetails { productId, availableStock, reservedStock }` |
| `ReserveStock` | `productId`, positive `quantity`, required UUID `reservationId` | `{ success: true, message }` |
| `ReleaseStock` | same | `{ success: true, message }` |
| `DeductStock` | same | `{ success: true, message }` |

For every mutating RPC, callers must provide a stable UUID `reservationId` and reuse it on retry. Reservation identity should be derived from `orderId + orderLineId`. Duplicate reserve while `RESERVED`, duplicate release after `RELEASED`, and duplicate deduct after `DEDUCTED` are no-ops. Missing or blank IDs are rejected; legacy quantity-only mutation behavior has been removed.

State rules: only `RESERVED` can release or deduct. `RELEASED` cannot deduct; `DEDUCTED` cannot release; re-reserving an existing released/deducted ID fails. A reused ID must have the same product and quantity.

gRPC maps missing inventory to `NOT_FOUND` and invalid business preconditions (including insufficient stock) to `FAILED_PRECONDITION`. Callers must send the allow-listed `x-internal-caller` identity; staging/production additionally require mTLS (`INVENTORY_GRPC_REQUIRE_MTLS=true`).

## REST errors

| Condition | Status |
| --- | --- |
| Missing/invalid JWT | `401` |
| Insufficient role | `403` |
| Invalid body | `400` field-validation map |
| Existing inventory on create | `409` `ApiErrorResponse` |
| Missing inventory / non-owned product | `404` `ApiErrorResponse` |

OpenAPI: `/v3/api-docs`; Swagger UI: `/swagger-ui.html`.
