# Order Service API

Base path: `/api/v1`.

| Endpoint | Access | Behaviour |
| --- | --- | --- |
| `POST /orders` | Customer | Creates an order from `productId` and `quantity` only. |
| `GET /orders`, `GET /orders/{orderId}` | Customer | Reads only the current user's orders. |
| `PUT /orders/{orderId}/cancel` | Customer | Cancels an eligible own order and queues stock release. |
| `/admin/orders/**` | ADMIN | Platform-wide listing and status management. |
| `GET /seller/orders` | SELLER, ADMIN | Returns only line items for the JWT seller's products. |

Order items never accept a client price. Order Service obtains the current price from Product Service.
