# Gateway route configuration

Gateway routes are delivered by the Config Server. Keep environment-specific route URLs out of
the application source tree. The following is the baseline for the configuration repository's
`dev/gateway-service-dev.yml`; replace localhost addresses with service DNS names in deployed environments.

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: auth-service
          uri: http://localhost:8081
          predicates:
            - Path=/api/v1/auth/**,/api/v1/users/**,/api/v1/admin/**,/oauth2/**,/.well-known/**
          filters:
            - name: RequestRateLimiter
              args:
                key-resolver: "#{@userOrIpKeyResolver}"
                redis-rate-limiter.replenishRate: 20
                redis-rate-limiter.burstCapacity: 40
            - name: CircuitBreaker
              args:
                name: auth-service
                fallbackUri: forward:/__fallback/auth-service

resilience4j:
  circuitbreaker:
    instances:
      auth-service:
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
  timelimiter:
    instances:
      auth-service:
        timeoutDuration: 5s
```

Apply the `RequestRateLimiter` only when Redis is running. For local work without Redis, omit
that filter from the `dev` route config. Do not retry non-idempotent write routes at the gateway.
