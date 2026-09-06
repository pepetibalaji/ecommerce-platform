# Gateway Service current implementation

## Implemented

* Reactive Spring Cloud Gateway edge with externally supplied routes.
* Stateless JWT validation, public path allowlist, admin/seller gates, and custom role conversion.
* Credentialed CORS with explicit configured origins.
* User-or-IP and IP-only rate-limit key resolvers; optional trusted-proxy header support.
* Redis rate-limit dependency failures and circuit-breaker fallback responses as RFC-style 503 Problem Details.
* Bounded downstream HTTP timeouts, health/info/prometheus/gateway actuator exposure, structured logs, and tracing dependencies.

## Important limitations

* Route inventory, quotas, circuit-breaker use, token relay behavior, and upstream URIs are external configuration; they cannot be inferred from source alone.
* Gateway is not the sole authorization boundary; each service must retain route/ownership enforcement.
* No business persistence, Kafka events, API aggregation transformation, WAF, TLS termination policy, or hard-coded rate-limit quota is implemented here.
