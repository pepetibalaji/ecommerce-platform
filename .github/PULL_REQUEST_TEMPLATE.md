## Summary

<!-- Briefly explain what this PR changes and why. -->

Example:
- Migrates Order Service to OAuth2 Resource Server
- Adds Kafka `order-created` event publishing
- Fixes JWT `userId` identity handling
- Updates tests and config

---

## Type of Change

- [ ] Feature
- [ ] Bug fix
- [ ] Refactor
- [ ] Security
- [ ] Test
- [ ] Documentation
- [ ] Config / Infrastructure
- [ ] Tech debt / Cleanup

---

## Services / Modules Affected

- [ ] `auth-service`
- [ ] `product-service`
- [ ] `inventory-service`
- [ ] `cart-service`
- [ ] `order-service`
- [ ] `gateway-service`
- [ ] `common-security`
- [ ] `common-exception`
- [ ] `common-proto`
- [ ] `common-grpc`
- [ ] `common-events`
- [ ] Config repo
- [ ] Docker / infrastructure
- [ ] Documentation

---

## What Changed

<!-- List the main code/config changes. -->

- 
- 
- 

---

## Architecture Impact

<!-- Explain whether this changes REST APIs, gRPC contracts, Kafka events, DB schema, security, or service communication. -->

- [ ] REST API changed
- [ ] gRPC contract changed
- [ ] Kafka topic/event changed
- [ ] Database schema changed
- [ ] OAuth2/security behavior changed
- [ ] Config Server properties changed
- [ ] Docker/local infrastructure changed
- [ ] No architecture impact

Details:

```text
Add details here.
```

---

## API Changes

<!-- Fill only if API behavior changed. -->

| Method | Endpoint | Change |
|---|---|---|
|  |  |  |

---

## Security / OAuth2 Checklist

- [ ] Public endpoints are intentionally public
- [ ] Protected endpoints require authentication
- [ ] Admin endpoints require `ROLE_ADMIN`
- [ ] Resource Server config uses `issuer-uri` / `jwk-set-uri`
- [ ] No custom JWT parsing added
- [ ] No `JwtAuthenticationFilter` added to resource services
- [ ] User identity comes from JWT `userId` claim, not `authentication.getName()`
- [ ] No secrets committed

Notes:

```text
Add details here.
```

---

## Kafka / Event Checklist

- [ ] Kafka producer/consumer changed
- [ ] Topic name added or updated
- [ ] Event payload added or updated
- [ ] Serializer/deserializer configured
- [ ] Topic creation documented
- [ ] Not applicable

Kafka details:

```text
Topic:
Event:
Producer:
Consumer:
```

---

## gRPC Checklist

- [ ] Proto contract changed
- [ ] gRPC client changed
- [ ] gRPC server changed
- [ ] Timeout/retry behavior changed
- [ ] Not applicable

gRPC details:

```text
Service:
Method:
Request:
Response:
```

---

## Database / Flyway Checklist

- [ ] Flyway migration added
- [ ] Entity changed
- [ ] Repository changed
- [ ] Existing data compatibility considered
- [ ] Not applicable

Migration details:

```text
Migration file:
Backward compatible:
Rollback notes:
```

---

## Config Changes

<!-- Mention config repo or application.yml changes. -->

- [ ] `application-dev.yml`
- [ ] `auth-service-dev.yml`
- [ ] `product-service-dev.yml`
- [ ] `inventory-service-dev.yml`
- [ ] `cart-service-dev.yml`
- [ ] `order-service-dev.yml`
- [ ] Local `application.yml`
- [ ] `.env.example`
- [ ] Not applicable

Config details:

```text
Add details here.
```

---

## Testing

### Commands Run

```bash
# Example:
mvn -pl auth-service -am clean test
mvn -pl product-service -am clean test
mvn -pl inventory-service -am clean test
mvn -pl cart-service -am clean test
mvn -pl order-service -am clean test
```

### Test Coverage Added / Updated

- [ ] Unit tests
- [ ] Controller tests
- [ ] Repository tests
- [ ] Integration tests
- [ ] Manual smoke tests
- [ ] Not applicable

Details:

```text
Add details here.
```

---

## Manual Validation

<!-- Add Swagger/API/Kafka/gRPC verification if applicable. -->

- [ ] Swagger loads successfully
- [ ] Protected API returns `401` without token
- [ ] Admin API returns `403` for non-admin token
- [ ] Valid token succeeds
- [ ] Kafka topic receives expected event
- [ ] gRPC call succeeds
- [ ] Actuator health is UP
- [ ] Not applicable

Validation notes:

```text
Add details here.
```

---

## Screenshots / Logs

<!-- Add screenshots, curl output, Swagger screenshots, Kafka consumer logs, or test output if useful. -->

```text
Paste relevant logs here.
```

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
- [ ] README/docs updated if behavior changed
- [ ] Backward compatibility considered