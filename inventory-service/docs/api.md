# Inventory Service API

Base path: `/api/v1`.

| Endpoint | Access | Behaviour |
| --- | --- | --- |
| `POST /admin/inventory` | ADMIN | Creates inventory for a product (legacy/manual operation). |
| `GET`, `PUT /admin/inventory/{productId}` | ADMIN | Reads or updates any product inventory. |
| `POST /seller/inventory` | SELLER, ADMIN | Creates inventory only for an owned product. |
| `GET`, `PUT /seller/inventory/{productId}` | SELLER, ADMIN | Reads or updates only owned inventory. |

Order Service uses gRPC `GetInventory`, `ReserveStock`, `ReleaseStock`, and `DeductStock`.
