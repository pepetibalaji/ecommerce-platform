# Pull Request

## Summary

<!-- Briefly explain what this PR changes and why. -->

Example:

- Adds Payment Service provider abstraction and Stripe Test Mode checkout
- Adds customer/admin payment REST APIs
- Adds Stripe webhook signature verification and payment reconciliation
- Updates Config Server properties for payment provider configuration
- Keeps Kafka publishing, gRPC APIs, and refund execution out of scope for PAYMENT-103

---

## Jira / Epic

| Item        | Value |
| ----------- | ----- |
| Epic        |       |
| Jira Task   |       |
| Branch      |       |
| Related PRs |       |

---

## Type of Change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Security
- [ ] Test
- [ ] Documentation
- [ ] Config / Infrastructure
- [ ] Database / Flyway
- [ ] Observability
- [ ] Tech debt / Cleanup

---

## Services / Modules Affected

### Business Services

- [ ] `auth-service`
- [ ] `product-service`
- [ ] `inventory-service`
- [ ] `cart-service`
- [ ] `order-service`
- [ ] `payment-service`
- [ ] `gateway-service`
- [ ] `notification-service`
- [ ] `shipping-service`
- [ ] Other:

### Shared Modules

- [ ] `common-security`
- [ ] `common-exception`
- [ ] `common-proto`
- [ ] `common-grpc`
- [ ] `common-events`
- [ ] `common-redis`
- [ ] Other:

### Platform / Infrastructure

- [ ] Config repo
- [ ] Docker / Docker Compose
- [ ] Observability: OpenTelemetry / Prometheus / Grafana / Tempo
- [ ] CI/CD
- [ ] Documentation
- [ ] Not applicable

---

## What Changed

<!-- List the main code/config changes. -->

-
-
-

---

## Architecture Impact

<!-- Explain whether this changes REST APIs, gRPC contracts, Kafka events, DB schema, security, config, provider integrations, or service communication. -->

- [ ] REST API changed
- [ ] gRPC contract changed
- [ ] gRPC client/server behavior changed
- [ ] Kafka topic/event changed
- [ ] Database schema changed
- [ ] OAuth2/security behavior changed
- [ ] Config Server properties changed
- [ ] Provider/payment gateway behavior changed
- [ ] Observability changed
- [ ] Docker/local infrastructure changed
- [ ] No architecture impact

Details:

```text
Add details here.
```

---

## Architecture Rules Checklist

- [ ] Service-specific business logic remains inside the owning service
- [ ] Shared modules only contain reusable infrastructure
- [ ] No service-specific `SecurityFilterChain` added to `common-security`
- [ ] No duplicate event classes added outside `common-events`
- [ ] No duplicate protobuf contracts added outside `common-proto`
- [ ] REST APIs are external-facing through Gateway
- [ ] gRPC is used only for internal synchronous communication
- [ ] Kafka is used only for async business events
- [ ] Config is externalized through Config Server
- [ ] User ownership uses JWT `userId` claim
- [ ] Business errors use `common-exception`
- [ ] No raw credentials, provider secrets, or tokens committed

Notes:

```text
Add details here.
```

---

## API Changes

<!-- Fill only if REST API behavior changed. -->

| Method | Endpoint | Auth | Change |
| ------ | -------- | ---- | ------ |
|        |          |      |        |

Example:

| Method | Endpoint                                             | Auth             | Change                                      |
| ------ | ---------------------------------------------------- | ---------------- | ------------------------------------------- |
| POST   | `/api/v1/payments/orders/{orderId}/checkout-session` | Customer JWT     | Creates or returns payment checkout session |
| GET    | `/api/v1/payments/me?page=&size=`                    | Customer JWT     | Returns current user payment history        |
| GET    | `/api/v1/admin/payments`                             | `ROLE_ADMIN`     | Returns admin payment list                  |
| POST   | `/api/v1/payments/webhooks/stripe`                   | Stripe signature | Receives Stripe webhook callback            |

---

## Security / OAuth2 Checklist

- [ ] Public endpoints are intentionally public
- [ ] Webhook endpoints are public but provider-signature protected
- [ ] Protected customer endpoints require authentication
- [ ] Admin endpoints require `ROLE_ADMIN`
- [ ] Resource Server config uses `issuer-uri` / `jwk-set-uri`
- [ ] No custom JWT parsing added
- [ ] No `JwtAuthenticationFilter` added to resource services
- [ ] User identity comes from JWT `userId` claim, not `authentication.getName()`
- [ ] Provider secrets are externalized
- [ ] No secrets committed
- [ ] No raw card data is accepted, stored, or logged

Notes:

```text
Add details here.
```

---

## Payment Provider Checklist

<!-- Fill if this PR touches payment provider integration. -->

- [ ] Provider abstraction added/updated
- [ ] `PaymentGateway` interface added/updated
- [ ] `PaymentGatewayFactory` added/updated
- [ ] Stripe Test Mode integration added/updated
- [ ] Sandbox provider added/updated
- [ ] Razorpay adapter boundary added/updated
- [ ] Checkout session creation added/updated
- [ ] Webhook signature verification added/updated
- [ ] Webhook idempotency handled
- [ ] Duplicate provider events return safe response
- [ ] Unsupported provider events are ignored safely
- [ ] Refund flow added/updated
- [ ] Not applicable

Provider details:

```text
Active provider:
Provider mode:
Webhook events handled:
Out of scope:
```

---

## Kafka / Event Checklist

- [ ] Kafka producer changed
- [ ] Kafka consumer changed
- [ ] Topic name added or updated
- [ ] Event payload added or updated
- [ ] Event class added/updated in `common-events`
- [ ] Uses `KafkaTopics` constants
- [ ] Serializer/deserializer configured
- [ ] Topic creation documented
- [ ] Idempotency considered
- [ ] Retry/DLQ behavior changed
- [ ] Not applicable

Kafka details:

```text
Topic:
Event:
Producer:
Consumer:
Key:
Out of scope:
```

---

## gRPC Checklist

- [ ] Proto contract changed
- [ ] `.proto` file added/updated in `common-proto`
- [ ] Java stubs regenerated
- [ ] gRPC client changed
- [ ] gRPC server changed
- [ ] Deadline/timeout behavior changed
- [ ] Exception mapping changed
- [ ] Local plaintext behavior retained
- [ ] Not applicable

gRPC details:

```text
Service:
Method:
Request:
Response:
Deadline:
Out of scope:
```

---

## Database / Flyway Checklist

- [ ] Flyway migration added
- [ ] Entity changed
- [ ] Repository changed
- [ ] Index/constraint added or changed
- [ ] Existing data compatibility considered
- [ ] Rollback notes added
- [ ] Not applicable

Migration details:

```text
Migration file:
Tables changed:
Backward compatible:
Rollback notes:
```

---

## Config Server Changes

<!-- Mention config repo or local application.yml changes. -->

- [ ] `application-dev.yml`
- [ ] `auth-service-dev.yml`
- [ ] `product-service-dev.yml`
- [ ] `inventory-service-dev.yml`
- [ ] `cart-service-dev.yml`
- [ ] `order-service-dev.yml`
- [ ] `payment-service-dev.yml`
- [ ] `gateway-service-dev.yml`
- [ ] Local service `application.yml`
- [ ] `.env.example`
- [ ] Not applicable

Config details:

```text
Added/updated keys:
Sensitive values:
Encrypted/secret handling:
Required local env vars:
```

---

## Observability Checklist

- [ ] Actuator health verified
- [ ] Prometheus metrics exposed
- [ ] OpenTelemetry traces exported
- [ ] Trace ID propagated/logged
- [ ] Response `X-Trace-Id` behavior changed
- [ ] Grafana/Tempo visibility validated
- [ ] Structured logs reviewed
- [ ] Not applicable

Observability details:

```text
Health endpoint:
Prometheus endpoint:
Trace validation:
Dashboard/log notes:
```

---

## Testing

### Commands Run

```bash
# Add exact commands used.

mvn -pl <module-name> -am clean compile
mvn -pl <module-name> -am clean verify
```

### Test Coverage Added / Updated

- [ ] Unit tests
- [ ] Mapper tests
- [ ] Service tests
- [ ] Controller tests
- [ ] Repository tests
- [ ] Integration tests
- [ ] Kafka tests
- [ ] gRPC tests
- [ ] Provider sandbox tests
- [ ] Manual smoke tests
- [ ] Not applicable

Details:

```text
Add details here.
```

---

## Manual Validation

<!-- Add Swagger/API/Kafka/gRPC/payment-provider verification if applicable. -->

- [ ] Service starts successfully
- [ ] Swagger loads successfully
- [ ] Actuator health is UP
- [ ] Prometheus endpoint works
- [ ] Protected API returns `401` without token
- [ ] Admin API returns `403` for non-admin token
- [ ] Valid customer token succeeds
- [ ] JWT `userId` ownership validated
- [ ] Kafka topic receives expected event
- [ ] gRPC call succeeds
- [ ] Flyway migration executed
- [ ] Stripe checkout session created
- [ ] Stripe test payment completed
- [ ] Stripe webhook delivered
- [ ] Payment DB status updated correctly
- [ ] Duplicate webhook handled idempotently
- [ ] Not applicable

Validation notes:

```text
Add details here.
```

---

## Screenshots / Logs

<!-- Add Swagger screenshots, curl output, Stripe CLI logs, Kafka consumer logs, gRPC output, or test output if useful. -->

```text
Paste relevant logs here.
```

---

## Out of Scope

<!-- Explicitly list what this PR does not implement. -->

-
-
-

Example for PAYMENT-102:

- Kafka producer/consumer changes are out of scope
- gRPC APIs are out of scope
- Refund execution is out of scope
- Retry/DLQ/outbox are out of scope

---

## Risks / Rollback Plan

### Risks

-
-

### Rollback Plan

```text
Describe how to rollback this change if needed.
```

---

## Checklist Before Merge

- [ ] Code builds locally
- [ ] Tests pass
- [ ] No secrets or credentials committed
- [ ] Swagger/OpenAPI updated if APIs changed
- [ ] Config Server files updated if runtime config changed
- [ ] Flyway migration added if schema changed
- [ ] Kafka topic/event documented if event changed
- [ ] gRPC proto regenerated if contract changed
- [ ] Shared modules do not contain service-specific business policy
- [ ] User ownership uses JWT `userId`
- [ ] Common exceptions used for business errors
- [ ] Observability remains enabled
- [ ] README/docs updated if behavior changed
- [ ] Backward compatibility considered
