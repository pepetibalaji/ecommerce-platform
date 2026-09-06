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

Allowed origins are `GATEWAY_CORS_ALLOWED_ORIGINS` (default `http://localhost:3000,http://localhost:4200`). Methods: GET, POST, PUT, PATCH, DELETE, OPTIONS. Allowed headers: Authorization, Content-Type, Accept, X-Requested-With. Credentials are allowed; trace headers `X-Trace-Id` and `X-Span-Id` are exposed. Requests that pass route/security policies are proxied unchanged to their configured backend.

## Dependency responses

`/__fallback/**` is an internal route target for configured circuit-breaker fallbacks. It returns `503 application/problem+json`:

```json
{ "type": "about:blank", "title": "Upstream service unavailable", "status": 503, "detail": "Please retry shortly." }
```

A Redis rate-limiter failure is also translated to a `503 application/problem+json` with title `Rate limiting unavailable`. These paths should not be exposed as application APIs.
