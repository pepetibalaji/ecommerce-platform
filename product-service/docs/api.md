# Product Service API and contracts

Base path: `/api/v1`. All bodies are JSON. Product IDs and seller IDs are UUIDs. Public catalog reads do not require authentication; management routes require a bearer JWT with the indicated role.

## Product payloads

### Create product request

```json
{
  "name": "iPhone 15",
  "description": "Apple smartphone",
  "price": 999.00,
  "category": "Mobile",
  "brand": "Apple",
  "imageUrls": ["https://cdn.example.com/products/iphone-15.jpg"]
}
```

| Field | Type | Rules |
| --- | --- | --- |
| `name` | string | Required; non-blank. |
| `description`, `category`, `brand` | string | Optional. |
| `price` | decimal | Required and strictly positive. |
| `imageUrls` | list of strings | Optional; at most 10 URLs, each HTTPS and at most 2048 characters. Omitted/null becomes `[]`. |

### Update product request

Update requires the same fields and validation as create, plus optional `active`.

```json
{
  "name": "iPhone 15 Pro",
  "description": "Updated product",
  "price": 1199.00,
  "category": "Mobile",
  "brand": "Apple",
  "active": false,
  "imageUrls": []
}
```

This is a full replacement of all descriptive fields: omitted optional string fields become `null`, and omitted/null `imageUrls` become an empty list. Omitted `active` preserves its existing value.

### Product response

```json
{
  "id": "11111111-1111-1111-1111-111111111111",
  "sellerId": "22222222-2222-2222-2222-222222222222",
  "active": true,
  "name": "iPhone 15",
  "description": "Apple smartphone",
  "price": 999.00,
  "category": "Mobile",
  "brand": "Apple",
  "imageUrls": ["https://cdn.example.com/products/iphone-15.jpg"],
  "createdAt": "2026-09-06T10:15:30",
  "updatedAt": "2026-09-06T10:15:30"
}
```

Timestamps are `LocalDateTime` values without an offset. Legacy Mongo documents whose `active` field is null are returned as `active: true`.

## Public catalog endpoints

| Method and path | Query parameters | Behavior |
| --- | --- | --- |
| `GET /products` | `page` (default 0), `size` (default 10), optional `category`, `minPrice`, `maxPrice` | Returns Spring `Page<ProductResponse>`. |
| `GET /products/{productId}` | — | Returns a product by UUID. |

Filtering is implemented only for these combinations: category alone, both price bounds together, or category plus both bounds. Supplying only `minPrice` or only `maxPrice` currently falls back to an unfiltered list. Public list results exclude products whose `active` value is explicitly `false`; legacy documents without this field remain visible. A direct product-by-ID read can still return an inactive product so checkout can reject it explicitly.

A page response contains `content`, paging metadata, and total counts per Spring Data's standard JSON serialization.

## Admin endpoints

All require `ADMIN`. `sellerId` is mandatory UUID query data on creation and determines the product owner.

| Method and path | Behavior | Success |
| --- | --- | --- |
| `POST /admin/products?sellerId={sellerId}` | Creates one product, assigns UUID/timestamps and `active=true`, persists it, then starts event publication. | `201` product |
| `POST /admin/products/bulk?sellerId={sellerId}` | Validates and saves every supplied product, then starts publication for each. | `201` product list |
| `PUT /admin/products/{productId}` | Fully updates an existing product. | `200` product |
| `DELETE /admin/products/{productId}` | Hard-deletes an existing product. | `204` empty body |

## Seller endpoints

All require `SELLER` or `ADMIN`; seller identity is parsed from JWT claim `userId`.

| Method and path | Behavior | Success |
| --- | --- | --- |
| `POST /seller/products` | Creates a product owned by the JWT user. An ADMIN using this route creates under their own JWT user ID. | `201` product |
| `GET /seller/products?page=0&size=10` | Lists only products with `sellerId` equal to the JWT user ID, including inactive products. | `200` page |
| `PUT /seller/products/{productId}` | Seller can update an owned product; ADMIN can update any product. Cross-seller access is reported as not found. | `200` product |
| `DELETE /seller/products/{productId}` | Seller can delete an owned product; ADMIN can delete any product. Cross-seller access is reported as not found. | `204` empty body |

## Errors

| Condition | Status | Body |
| --- | --- | --- |
| Missing/invalid management token | `401` | Spring Security response |
| Role not permitted | `403` | `ApiErrorResponse` where handled |
| Invalid UUID, invalid page/size, or invalid request argument | `400` | Framework/shared error response |
| Validation failure | `400` | JSON map: field name → validation message |
| Missing product or seller cross-ownership mutation | `404` | `ApiErrorResponse` |

`ApiErrorResponse` contains `timestamp`, `status`, `error`, `message`, and `path`; do not parse its message as a stable contract. OpenAPI is at `/v3/api-docs`; Swagger UI is at `/swagger-ui.html`.
