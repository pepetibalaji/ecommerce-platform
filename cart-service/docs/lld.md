# Low-Level Design

`CartController` delegates to `CartService`, which persists `Cart` and `CartItem` through `CartRedisRepository`. JWT user identity scopes every operation.
