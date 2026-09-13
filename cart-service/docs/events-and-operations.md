# Cart Service events and operations

## Events and dependencies

HTTP cart operations use Redis directly. An `order-completed` Kafka consumer also removes purchased quantities according to the lifecycle policy below. Cart Service has no synchronous Product/Inventory integration; checkout must execute its own catalog, price, and inventory validation.

## Configuration

| Setting | Default | Effect |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | Spring profile. |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Optional Config Server URL. |
| `CART_CUSTOMER_TTL` | `7d` | Customer cart TTL. |
| `CART_GUEST_TTL` | `30d` | Guest cart and cookie TTL. |
| `CART_GUEST_COOKIE_NAME` | `guestId` | Guest identity cookie. |
| `CART_GUEST_COOKIE_SAME_SITE` | `Lax` | SameSite cookie attribute. |
| `CART_GUEST_COOKIE_SECURE` | `false` | Secure cookie attribute; enable for HTTPS. |
| `OBSERVABILITY_LOG_FILE` | `../logs/cart-service.json` | Structured log file. |

Redis, JWT issuer/JWK, management exposure, and tracing configuration are supplied by the shared/config-server setup for the environment.

## Observability

Public endpoints are `/actuator/health`, `/actuator/info`, `/actuator/prometheus`, `/v3/api-docs`, and `/swagger-ui.html`. Logs use Logstash structured formatting; the module includes Prometheus and OpenTelemetry tracing dependencies.

## Failure and operation notes

* Redis connection/resource failures return `503 CART_REDIS_UNAVAILABLE` with `Retry-After: 1`.
* TTL expiry or eviction makes a cart missing: reads return an unpersisted empty snapshot, while item update/remove returns `404`.
* Reads do not acquire mutation locks. Customer/guest write contention returns `409 CART_LOCK_CONTENTION`; use bounded retries and retain the same idempotency key where supported.
* Merge commits the customer snapshot, guest deletion, and replay record in one Redis transaction.
* For cross-site frontends, configure SameSite, Secure, and browser credential behavior deliberately.

Run `mvn spring-boot:run` from `cart-service` with Redis and JWT/config dependencies available. Use [`api/cart.http`](../../api/cart.http) for local requests. Avoid production-wide Redis key scans as routine verification.
# Cart lifecycle policy

Cart Service retains carts when an order is created, when payment fails, and when an order is cancelled. On the `order-completed` event emitted after confirmed payment, Cart Service removes only the quantities present in that order. The consumer is idempotent by event ID, so duplicate Kafka deliveries do not remove items twice. The browser never clears the authoritative cart.
