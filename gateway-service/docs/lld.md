# Gateway Service low-level design

## Security

`SecurityConfig` installs a stateless reactive OAuth2 resource-server chain. Matcher order permits explicit public paths first, then applies admin/seller role gates, then requires JWT for everything else. The custom JWT converter supports either a collection/string `roles` claim or scalar `role` fallback. CSRF is disabled for this stateless API gateway.

## CORS and rate-limit identity

One CORS configuration applies to `/**`. It supports credentialed browser requests, so allowed origins must be exact configured origins rather than wildcard values.

`userOrIpKeyResolver` is primary for route filters: authenticated requests key by `userId`, subject, or principal; anonymous requests key by IP. `ipKeyResolver` always keys by IP. The gateway ignores `X-Forwarded-For`/`X-Real-IP` by default, preventing client spoofing. Set `gateway.rate-limit.trust-forwarded-for=true` only behind a trusted proxy that overwrites these headers.

Rate limiting itself is route configuration: this module supplies key resolvers and Redis dependency support but does not hard-code quotas or filters. The gateway error handler converts unhandled Redis connectivity and timeout exceptions before a committed response into retryable 503 Problem Details.

## Fallback and observability

`GatewayFallbackController` is a catch-all controller below `/__fallback/**`; configured circuit-breaker filters can forward there. Management exposes `health`, `info`, `prometheus`, and `gateway`; structured logs use Logstash format. No token relay filter is defined in Java—whether Authorization is forwarded/altered is determined by Spring Gateway defaults and the externally configured route filters.
