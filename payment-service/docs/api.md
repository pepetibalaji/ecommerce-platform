# API Contract

| Endpoint | Access | Purpose |
| --- | --- | --- |
| `POST /api/v1/payments/orders/{orderId}/checkout-session` | Authenticated | Start checkout for an order. |
| `GET /api/v1/payments/me` | Authenticated | List caller payments. |
| `GET /api/v1/payments/orders/{orderId}` | Authenticated | Get caller payment for an order. |
| `GET /api/v1/payments/{paymentId}` | Authenticated | Get caller payment by ID. |
| `GET /public/payments/success`, `/cancel` | Public | Provider redirect destinations. |
| `POST /api/v1/payments/webhooks/stripe`, `/razorpay` | Provider | Signed provider callbacks. |
| `GET /api/v1/admin/payments`, `/{paymentId}` | ADMIN | Platform payment visibility. |
| `POST /api/v1/admin/payments/{paymentId}/refund` | ADMIN | Initiate refund. |
