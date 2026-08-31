# Product Service API

Base path: `/api/v1`.

| Endpoint | Access | Behaviour |
| --- | --- | --- |
| `GET /products`, `GET /products/{productId}` | Public | Reads current catalog details and price. |
| `POST /admin/products?sellerId={uuid}` | ADMIN | Creates a product for the specified seller. |
| `POST /admin/products/bulk?sellerId={uuid}` | ADMIN | Bulk creates products for the specified seller. |
| `PUT`, `DELETE /admin/products/{productId}` | ADMIN | Platform-wide product administration. |
| `POST /seller/products` | SELLER, ADMIN | Creates a product owned by JWT `userId`. |
| `GET /seller/products` | SELLER, ADMIN | Lists products owned by JWT `userId`. |
| `PUT`, `DELETE /seller/products/{productId}` | SELLER, ADMIN | Sellers may mutate only owned products. |

`active=false` makes a product unavailable for new orders.
