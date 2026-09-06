# Gateway Service high-level design

```text
Client -> Gateway (CORS + JWT + configured route/filter) -> domain service
                     |                  |
                     |                  +--> optional Redis rate-limit counters
                     +--> circuit-breaker fallback / dependency problem response
```

Gateway runs WebFlux/Spring Cloud Gateway and provides platform edge policy. It loads route definitions from Config Server/deployment configuration, rather than compiling route mappings into Java. It does not inspect/transform domain payloads or own a database/Kafka consumer.

Dependencies: JWT issuer/JWK resource-server configuration, Config Server, configured downstream URIs, optional Redis if route filters enable rate limiting, Resilience4j filters if configured, and Actuator/tracing/logging infrastructure. HTTP client defaults bound downstream connect time to 1 second and response time to 5 seconds.

The downstream services remain their own API and authorization authorities. Public edge paths must correspond to downstream public paths; a deployment route/configuration review is required whenever a new endpoint is added.
