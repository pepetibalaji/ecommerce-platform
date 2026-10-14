# Gateway Service visual map

Boxes inside the boundary are internal components; cylinders are storage. Arrows show request or configuration dependencies, not a strict filter execution order.

```mermaid
flowchart TD
    B["Browser / frontend"] --> C

    subgraph GATEWAY["Gateway service"]
        C["CORS + security filters"] --> J["JWT validation"]
        J --> F["Configured route filters<br/>rate limits / circuit breakers"]
        F --> R["HTTP routing"]
        F --> FB["Fallback / dependency error response"]
    end

    J -->|"Fetch public verification keys"| A["Auth JWKS"]
    F -->|"When rate limiting is configured"| RED[("Redis counters")]
    CFG["Config Server"] -->|"Route and policy configuration"| F
    R --> S["Configured downstream services<br/>Auth / Product / Cart / Order<br/>Payment / Inventory / Notification"]
```

Route definitions come from external configuration; enabled routes and filters depend on the deployed profile. Public routes can allow anonymous requests. Gateway owns no business database or Kafka workflow, and downstream services enforce their own authorization.

Source: [SecurityConfig](../src/main/java/com/ecommerce/gateway/config/SecurityConfig.java), [GatewayJwtDecoderConfig](../src/main/java/com/ecommerce/gateway/config/GatewayJwtDecoderConfig.java), [GatewayRateLimitConfig](../src/main/java/com/ecommerce/gateway/config/GatewayRateLimitConfig.java), [application configuration](../src/main/resources/application.yml).

See [high-level design](hld.md) and [events and operations](events-and-operations.md) for deployment and routing details.
