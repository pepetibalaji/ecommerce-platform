# Cart Service events and operations

## Events and dependencies

The implemented service has no Kafka producer/consumer, outbox, database, or synchronous Product/Inventory integration. Cart changes are direct HTTP-to-Redis operations. Checkout must execute its own catalog, price, and inventory validation.

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

* Redis unavailability makes cart storage and locks fail; unhandled failures are returned as `500`.
* TTL expiry or eviction makes a cart missing: read creates an empty cart, while item update/remove returns `404`.
* Guest/merge lock contention becomes generic `500`; clients can retry thoughtfully.
* Merge saves the customer cart before deleting guest. If a failure occurs after save but before guest deletion, replaying the merge before deletion can add quantities again.
* For cross-site frontends, configure SameSite, Secure, and browser credential behavior deliberately.

Run `mvn spring-boot:run` from `cart-service` with Redis and JWT/config dependencies available. Use [`api/cart.http`](../../api/cart.http) for local requests. Avoid production-wide Redis key scans as routine verification.
