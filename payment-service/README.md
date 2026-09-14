# Payment Service

Payment Service prepares payments from trusted Order data, creates one recoverable checkout session per payment, processes verified webhooks, and executes durable refunds. Payment/refund transitions and outgoing Order events commit through a transactional outbox.

Browser redirects are informational. Only verified provider webhooks confirm/fail a payment; authenticated browser status requests read persisted state. The service uses UTC timestamps, owner authorization, stable safe errors, exact checkout-host validation and bounded pagination.

The runtime uses Java 21, Spring Boot, PostgreSQL/JPA/Flyway, Kafka, OAuth2 JWT, Actuator and OpenAPI. REST normally runs on port 8087. Legacy unauthenticated gRPC operations are disabled. Stripe requires explicit staging verification before enablement outside development; Sandbox is dev/test only and Razorpay remains disabled.

```powershell
mvn -pl payment-service -am test
mvn -pl payment-service spring-boot:run
```

Local operation requires PostgreSQL, Kafka, Config Server, a signed trusted Order lookup, JWT issuer/JWK configuration and the chosen provider configuration. Database integration tests require a working container runtime. Environment settings belong in the separate ecommerce-config-repo.

See [API and frontend contract](docs/api.md), [database migrations](docs/schema.md), [confirmation recovery](docs/confirmation-recovery.md), and [production reliability runbook](docs/production-reliability.md).
