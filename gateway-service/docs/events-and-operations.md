# Gateway Service events and operations

Gateway has no Kafka producer/consumer. Its operation is route/configuration driven.

## Required configuration

| Setting | Default | Effect |
| --- | --- | --- |
| `CONFIG_SERVER_URL` | `http://localhost:8888` | Optional external route/config source. |
| `GATEWAY_CONNECT_TIMEOUT_MS` | `1000` | Downstream HTTP connect bound. |
| `GATEWAY_RESPONSE_TIMEOUT` | `5s` | Downstream HTTP response bound. |
| `GATEWAY_CORS_ALLOWED_ORIGINS` | localhost ports 3000/4200 | Comma-separated exact browser origins. |
| `GATEWAY_TRUST_FORWARDED_FOR` | `false` | Trust proxy client-IP headers for rate-limit keys. |
| `OBSERVABILITY_LOG_FILE` | `../logs/gateway-service.json` | Structured log file. |

Also supply route definitions, backend URIs, JWT issuer/JWK configuration, and Redis connection/quota policy when rate limiting is enabled. Validate route predicates and public-path alignment in each deployed environment; they are not visible in the module source tree.

## Monitoring and recovery

Use `/actuator/gateway` to inspect active gateway state when exposed, `/actuator/health`, `/actuator/prometheus`, and structured logs. Monitor upstream 5xx/latency, route mismatch/404s, JWT rejection, Redis availability, rate-limit rejections, and fallback 503s. Redis rate-limit failure deliberately fails closed with 503. An upstream circuit-breaker fallback should be interpreted as an availability incident, not a domain response.
