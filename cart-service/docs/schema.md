# Cart Service data model

Cart Service has no relational schema. Redis stores a serialized `Cart` value per owner.

## Keys and expiry

| Purpose | Key | Default TTL | Used by |
| --- | --- | --- | --- |
| Customer cart | `cart:{userId}` | 7 days | Customer operations and merge destination |
| Guest cart | `guest-cart:{guestId}` | 30 days | Guest operations |
| Customer lock | `cart-lock:customer:{userId}` | 15 seconds | Merge |
| Guest lock | `cart-lock:guest:{guestId}` | 15 seconds | Guest operations and merge |

`cart.customer.ttl` and `cart.guest.ttl` configure cart expiry. Every save resets the appropriate full TTL; reads do not. Clear/final-item removal deletes the key immediately. An empty cart created by read/create remains until expiry.

## Value shape

```json
{
  "userId": "11111111-1111-1111-1111-111111111111",
  "items": [{ "itemId": "a8b7cc94-5e9b-40ac-8c22-5dc4b7f6f67c", "productId": "22222222-2222-2222-2222-222222222222", "quantity": 2 }],
  "updatedAt": "2026-09-06T10:15:30.123"
}
```

| Field | Type | Meaning |
| --- | --- | --- |
| `userId` | string | Customer JWT claim, or guest UUID for guest carts. |
| `items` | list | Cart lines; normal API mutations maintain one line per product. |
| `itemId` | string | Service-generated UUID used for update/remove. |
| `productId` | string | Opaque catalog reference; no product or price snapshot exists. |
| `quantity` | integer | Positive for API-created lines. |
| `updatedAt` | local datetime | Server-host last create/mutation time. |

Expiry, eviction, or Redis loss is treated as a missing cart—not as a recoverable order record.
