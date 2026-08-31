# API Gateway

## What this service is

API Gateway is the public HTTP entry point. It runs on port `8080`, routes `/api/v1/**` requests to the owning service, applies CORS/security policy, and validates JWTs before forwarding protected requests.

## Technology

- Java 21, Spring Boot, Spring Cloud Gateway (reactive/WebFlux)
- Spring Security OAuth2 Resource Server
- Redis capability for rate limiting
- Actuator, OpenAPI aggregation, structured logs

## End-to-end flow

```text
Client request with Bearer JWT
  -> Gateway validates JWT issuer/signature/expiry
  -> Gateway matches configured route
  -> Gateway forwards request to Auth, Product, Cart, Inventory, Order, Payment, or Notification Service
  -> service response returns through Gateway to client
```

Routes are loaded from `ecommerce-config-repo`; Gateway does not contain business logic or own business data.

## Run locally

Start Config Server first, then:

```bash
cd gateway-service
mvn spring-boot:run
```

## Required configuration

- Service URIs and public routes.
- Auth issuer/JWK settings.
- CORS origin settings.

## Current and next work

Current: routing, JWT validation, CORS, fallback/error handling. Next: stage TLS, active rate-limit policy, circuit breakers, and deployment-level WAF policy.
