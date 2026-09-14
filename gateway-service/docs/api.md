# Gateway Service API and edge contract

Gateway forwards configured downstream APIs and should not alter their request/response contracts. The source of truth for route IDs, predicates, upstream URIs, filters, circuit breakers, and rate-limit settings is external Config Server/deployment configuration, not this repository.

## Gateway authorization

| Path class | Gateway access rule |
| --- | --- |
| Auth registration/login/refresh/verification/password recovery paths | Public |
| `/api/v1/cart/guest/**` | Public |
| `/oauth2/**`, `/.well-known/**` | Public |
| Swagger/OpenAPI aggregation paths | Public |
| `/actuator/health/**`, `/actuator/info`, `/actuator/prometheus` | Public |
| `/mcp/**`, `/api/v1/ai/**`, `/api/v1/admin/**` | JWT role `ADMIN` |
| `/api/v1/seller/**` | JWT role `SELLER` or `ADMIN` |
| Everything else | Authenticated JWT |

JWT role conversion reads an array/string `roles` claim; if no authority results, it falls back to scalar `role`. Values become Spring `ROLE_*` authorities. Downstream services still enforce their own authorization and ownership rules, so gateway authorization is defense in depth rather than the only security boundary.

## CORS and forwarding

Allowed origins are `GATEWAY_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000,http://localhost:4200,http://localhost:5173`). Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Allowed headers: Authorization, Content-Type, Accept, X-Requested-With, Idempotency-Key. Credentials are allowed; trace headers `X-Trace-Id` and `X-Span-Id` are exposed. Requests that pass route/security policies are proxied unchanged to their configured backend.

## Checkout and cancellation rate limits

The external Config Server route definitions apply `RequestRateLimiter` before the general Order route:

| Route | Key | Default quota | Configuration |
| --- | --- | --- | --- |
| `POST /api/v1/orders` | Client IP | 2 requests/second, burst 5 | `GATEWAY_ORDER_CHECKOUT_RATE_LIMIT_PER_SECOND`, `GATEWAY_ORDER_CHECKOUT_RATE_LIMIT_BURST` |
| `PUT /api/v1/orders/*/cancel` | Client IP | 2 requests/second, burst 5 | `GATEWAY_ORDER_CANCEL_RATE_LIMIT_PER_SECOND`, `GATEWAY_ORDER_CANCEL_RATE_LIMIT_BURST` |

Each request consumes one token. A rejected request is `429`; browser clients must preserve safe input and honor `Retry-After` when the deployment provides it. The IP resolver only trusts forwarded client-IP headers when `GATEWAY_TRUST_FORWARDED_FOR=true` and the deployment proxy is trusted. Route IDs and values live in `ecommerce-config-repo` (`dev`, `stage`, and `prod` Gateway files), not in this service module.

## Dependency responses

`/__fallback/**` is an internal route target for configured circuit-breaker fallbacks. It returns `503 application/problem+json`:

```json
{ "type": "about:blank", "title": "Upstream service unavailable", "status": 503, "detail": "Please retry shortly." }
```

The gateway error handler translates unhandled Redis connectivity or timeout failures to a `503 application/problem+json` with title `Service temporarily unavailable`. These paths should not be exposed as application APIs.
