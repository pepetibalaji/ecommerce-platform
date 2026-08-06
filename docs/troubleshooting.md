# Troubleshooting Guide

This guide documents common local development issues for the ecommerce microservices platform.

The platform currently uses:

- Java 21
- Spring Boot 3.x
- Spring Cloud Config Server
- Spring Security OAuth2 / Resource Server
- PostgreSQL
- Redis
- Kafka
- gRPC
- Docker Compose
- Swagger/OpenAPI
- Actuator, Prometheus, Grafana, Zipkin

---

## 1. Config Server Issues

### Problem

Service starts, but expected config values are missing.

Common symptoms:

```text
No qualifying bean of type JwtDecoder available
```

```text
bootstrap.servers = [localhost:9092]
```

even though config repo has:

```yaml
localhost:29092
```

### Cause

The service may not be receiving the correct config from Config Server.

### Fix

Check that the local service `application.yml` has the correct app name and profile:

```yaml
spring:
  application:
    name: order-service

  profiles:
    active: dev

  config:
    import: optional:configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

Config Server should load:

```text
dev/order-service-dev.yml
```

Verify config is being served:

```powershell
Invoke-RestMethod http://localhost:8888/order-service/dev
```

Search for a specific property:

```powershell
Invoke-RestMethod http://localhost:8888/order-service/dev |
  ConvertTo-Json -Depth 30 |
  Select-String "bootstrap|issuer-uri|jwk-set-uri"
```

Expected for Order Service:

```text
localhost:29092
http://localhost:8081
http://localhost:8081/oauth2/jwks
```

### Checklist

- [ ] Config Server is running on port `8888`
- [ ] Service `spring.application.name` matches config filename
- [ ] Active profile is `dev`
- [ ] Config file is named correctly, for example `dev/order-service-dev.yml`
- [ ] Config repo changes are saved/committed if Git-backed
- [ ] Config Server has been restarted after config changes

---

## 2. Missing JwtDecoder Bean

### Problem

Service fails during startup:

```text
No qualifying bean of type 'org.springframework.security.oauth2.jwt.JwtDecoder' available
```

### Cause

Spring Boot cannot create a JWT decoder because the Resource Server JWT properties are missing at runtime.

### Fix

Add this to the service config in the config repo:

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${AUTH_ISSUER_URI:http://localhost:8081}
          jwk-set-uri: ${AUTH_JWK_SET_URI:http://localhost:8081/oauth2/jwks}
```

Also make sure the service has this dependency:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

### Verify Auth JWK endpoint

Start Auth Service and open:

```text
http://localhost:8081/oauth2/jwks
```

Expected:

```json
{
  "keys": [...]
}
```

---

## 3. Swagger Returns 401

### Problem

Swagger UI or OpenAPI JSON returns:

```text
HTTP 401
```

### Cause

The service security config is protecting Swagger endpoints.

### Fix

Whitelist Swagger and OpenAPI paths in each Resource Server:

```java
private static final String[] SWAGGER_WHITELIST = {
        "/swagger-ui.html",
        "/swagger-ui/**",
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/v3/api-docs.yaml",
        "/swagger-resources/**",
        "/webjars/**"
};
```

Use it in `SecurityConfig`:

```java
.authorizeHttpRequests(auth -> auth
        .requestMatchers(SWAGGER_WHITELIST).permitAll()
        .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
        .anyRequest().authenticated()
)
```

### Swagger URLs

| Service | Swagger URL |
|---|---|
| Auth | `http://localhost:8081/swagger-ui.html` |
| Product | `http://localhost:8082/swagger-ui.html` |
| Inventory | `http://localhost:8084/swagger-ui.html` |
| Cart | `http://localhost:8085/swagger-ui.html` |
| Order | `http://localhost:8086/swagger-ui.html` |

---

## 4. Duplicate `securityFilterChain` Bean

### Problem

Application fails with:

```text
Cannot register bean definition for bean 'securityFilterChain'
because there is already another bean with that name
```

### Cause

`common-security` defines a full `SecurityFilterChain`, and the service also defines its own `SecurityFilterChain`.

### Fix

`common-security` should not define service route rules.

Remove this from `common-security`:

```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    ...
}
```

`common-security` should only provide shared helpers:

```text
JwtAuthoritiesConverter
JwtAuthenticationConverter
JwtClaimConstants
JwtPrincipalUtils
```

Each service should own its own security rules:

```text
product-service/SecurityConfig.java
inventory-service/SecurityConfig.java
cart-service/SecurityConfig.java
order-service/SecurityConfig.java
```

---

## 5. Deprecated `oauth2ResourceServer().jwt()`

### Problem

IDE warning:

```text
The method jwt() from OAuth2ResourceServerConfigurer has been deprecated
```

### Fix

Replace:

```java
.oauth2ResourceServer(oauth2 -> oauth2.jwt())
```

with:

```java
.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        )
)
```

or, without a custom converter:

```java
.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(Customizer.withDefaults())
)
```

For this platform, use the converter because JWT role claim needs mapping to Spring authorities.

---

## 6. Kafka Keeps Connecting to `localhost:9092`

### Problem

Order Service logs show:

```text
bootstrap.servers = [localhost:9092]
```

and repeated errors:

```text
Bootstrap broker localhost:9092 disconnected
Connection to node -1 localhost/127.0.0.1:9092 could not be established
```

### Cause

The Spring Boot service is running on the host machine, but the Kafka Docker Compose host listener is exposed on `29092`.

### Correct Rule

| Runtime | Bootstrap Server |
|---|---|
| Spring Boot running locally from IDE/terminal | `localhost:29092` |
| Spring Boot running inside Docker network | `kafka:9092` |

### Fix

In config repo, for host-running services:

```yaml
spring:
  kafka:
    bootstrap-servers: localhost:29092
```

Do not use:

```yaml
localhost:9092
```

for host-running Spring Boot services.

### Verify Runtime Config

```powershell
Invoke-RestMethod http://localhost:8888/order-service/dev |
  ConvertTo-Json -Depth 30 |
  Select-String "bootstrap"
```

Expected:

```text
localhost:29092
```

Also check environment overrides:

```powershell
Get-ChildItem Env: |
  Where-Object { $_.Name -match "KAFKA|SPRING" }
```

If this exists:

```text
SPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

update it:

```powershell
$env:SPRING_KAFKA_BOOTSTRAP_SERVERS="localhost:29092"
```

---

## 7. Kafka Topic Not Present in Metadata

### Problem

```text
Topic order-created not present in metadata after 60000 ms
```

### Causes

- Kafka broker is not running
- Wrong bootstrap server
- Topic was not created
- Kafka advertised listener is wrong
- Kafka cluster data is corrupted/mismatched

### Fix

Check Kafka is reachable:

```powershell
Test-NetConnection localhost -Port 29092
```

Expected:

```text
TcpTestSucceeded : True
```

Create topic:

```powershell
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order-created --partitions 3 --replication-factor 1
```

Verify topics:

```powershell
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server kafka:9092 --list
```

Expected:

```text
order-created
```

Consume events:

```powershell
docker exec -it ecommerce-kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic order-created --from-beginning
```

---

## 8. Kafka Cluster ID Mismatch

### Problem

Kafka logs show:

```text
cluster id doesn't match
```

### Cause

Kafka persisted data belongs to one Zookeeper cluster, but the current Zookeeper instance has a different cluster ID.

This can happen when:

- Kafka data is persisted
- Zookeeper data is deleted or recreated
- Kafka starts with old broker metadata and new Zookeeper metadata

### Local Dev Fix

Stop Kafka and Zookeeper:

```powershell
docker compose stop kafka zookeeper
docker compose rm -f kafka zookeeper
```

Remove only Kafka data volume:

```powershell
docker volume rm ecommerce-platform_kafka-data
```

Start again:

```powershell
docker compose up -d zookeeper kafka
```

Recreate topics:

```powershell
docker exec -it ecommerce-kafka kafka-topics --bootstrap-server kafka:9092 --create --if-not-exists --topic order-created --partitions 3 --replication-factor 1
```

### Important

Avoid this unless you want to delete all local data:

```powershell
docker compose down -v
```

It deletes all volumes, including PostgreSQL and Redis.

---

## 9. Docker Compose Undefined Zookeeper Volume

### Problem

```text
service "zookeeper" refers to undefined volume zookeeper-data: invalid compose project
```

### Cause

Zookeeper service references volumes that are not declared in the top-level `volumes` block.

### Fix

Add these volumes:

```yaml
volumes:
  postgres-data:
  redis-data:
  kafka-data:
  zookeeper-data:
  zookeeper-log:
  grafana-data:
```

Zookeeper service:

```yaml
zookeeper:
  image: confluentinc/cp-zookeeper:7.6.1
  container_name: ecommerce-zookeeper
  restart: unless-stopped
  environment:
    ZOOKEEPER_CLIENT_PORT: 2181
    ZOOKEEPER_TICK_TIME: 2000
  ports:
    - "2181:2181"
  volumes:
    - zookeeper-data:/var/lib/zookeeper/data
    - zookeeper-log:/var/lib/zookeeper/log
  networks:
    - ecommerce-network
```

---

## 10. Kafka Cannot Serialize `OrderCreatedEvent`

### Problem

API response:

```json
{
  "status": 500,
  "message": "Can't convert value of class com.ecommerce.order.event.OrderCreatedEvent to class org.apache.kafka.common.serialization.StringSerializer specified in value.serializer"
}
```

### Cause

Kafka producer is configured with:

```text
StringSerializer
```

but Order Service is publishing:

```java
OrderCreatedEvent
```

### Fix

Use JSON serializer:

```yaml
spring:
  kafka:
    producer:
      acks: all
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      properties:
        spring.json.add.type.headers: false
```

Use typed Kafka template:

```java
private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
```

Publisher example:

```java
kafkaTemplate.send(
        "order-created",
        event.getOrderId().toString(),
        event
);
```

Expected startup log:

```text
value.serializer = class org.springframework.kafka.support.serializer.JsonSerializer
```

not:

```text
value.serializer = class org.apache.kafka.common.serialization.StringSerializer
```

---

## 11. Prometheus Endpoint Fails with `ApiErrorResponse`

### Problem

Calling:

```text
/actuator/prometheus
```

causes:

```text
No converter for ApiErrorResponse with preset Content-Type application/openmetrics-text
```

### Cause

The shared `GlobalExceptionHandler` catches errors from Actuator/Prometheus and tries to return JSON while the response content type is OpenMetrics.

### Fix

Scope the exception handler to application packages:

```java
@RestControllerAdvice(basePackages = "com.ecommerce")
public class GlobalExceptionHandler {
}
```

Also return JSON explicitly from API exception handlers:

```java
return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .contentType(MediaType.APPLICATION_JSON)
        .body(response);
```

Make sure this import is not present:

```java
import org.springframework.web.ErrorResponse;
```

Use your own response:

```java
ApiErrorResponse
```

---

## 12. `PageImpl` Serialization Warning

### Problem

Logs show:

```text
Serializing PageImpl instances as-is is not supported
```

### Cause

Spring warns that direct `PageImpl` serialization is not guaranteed to produce stable JSON.

### Fix

Add this to services that return `Page<T>` from controllers:

```java
package com.ecommerce.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

@Configuration
@EnableSpringDataWebSupport(
        pageSerializationMode =
                EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO
)
public class WebConfig {
}
```

This is useful for:

```text
GET /api/v1/orders
GET /api/v1/admin/orders
```

---

## 13. Lombok `@Builder` Ignores Initialized List

### Problem

Build warning:

```text
@Builder will ignore the initializing expression entirely
```

### Cause

Lombok builder ignores field initialization unless `@Builder.Default` is used.

### Example Problem

```java
private List<OrderItem> items = new ArrayList<>();
```

### Fix

```java
@Builder.Default
@OneToMany(
        mappedBy = "order",
        cascade = CascadeType.ALL,
        orphanRemoval = true
)
private List<OrderItem> items = new ArrayList<>();
```

---

## 14. Refresh Token Duplicate Key on `user_id`

### Problem

```text
duplicate key on refresh_tokens.user_id
```

### Cause

The database has a unique constraint on `refresh_tokens.user_id`, but the platform supports multiple devices and multiple browsers.

### Fix

Remove the unique constraint on `refresh_tokens.user_id`.

Flyway example:

```sql
ALTER TABLE refresh_tokens
DROP CONSTRAINT <unique_constraint_name>;
```

Keep a normal index:

```sql
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
ON refresh_tokens(user_id);
```

Expected model:

```text
users 1 -> many refresh_tokens
```

Login should not revoke all existing refresh tokens.

---

## 15. Token Version Missing in JWT

### Problem

Resource validation fails because token is missing:

```json
"tokenVersion": 0
```

### Cause

The validator expects `tokenVersion`, but token generation does not include it.

### Fix

In token generation:

```java
.claim("tokenVersion", user.getTokenVersion())
```

In token customizer:

```java
context.getClaims()
       .claim("tokenVersion", user.getTokenVersion());
```

Expected JWT claims:

```json
{
  "sub": "user@email.com",
  "userId": "uuid",
  "role": "CUSTOMER",
  "status": "ACTIVE",
  "tokenVersion": 0
}
```

---

## 16. `Jwt.Builder.id(...)` Does Not Exist

### Problem

Test code fails:

```text
The method id(String) is undefined for the type Jwt.Builder
```

### Fix

Use:

```java
.jti("jwt-id")
```

instead of:

```java
.id("jwt-id")
```

Example:

```java
Jwt.withTokenValue("access-token")
        .header("alg", "RS256")
        .subject("customer@example.com")
        .claim("userId", USER_ID.toString())
        .claim("role", "CUSTOMER")
        .claim("tokenVersion", 0L)
        .jti("jwt-id")
        .build();
```

If `jti(...)` is unavailable, use:

```java
.claim("jti", "jwt-id")
```

---

## 17. Auth Test Fails on `expiresIn`

### Problem

Test failure:

```text
No value at JSON path "$.expiresIn"
```

Actual response:

```json
"expiresInSeconds": 3600
```

### Fix

Change test assertion from:

```java
.andExpect(jsonPath("$.expiresIn").value(3600))
```

to:

```java
.andExpect(jsonPath("$.expiresInSeconds").value(3600))
```

---

## 18. Redis Blacklist Key Verification

### Check Keys

```bash
redis-cli
KEYS *
```

Expected:

```text
auth:blacklist:jti:<jti>
```

Check value:

```redis
GET auth:blacklist:jti:<jti>
```

Expected:

```text
1
```

Check TTL:

```redis
TTL auth:blacklist:jti:<jti>
```

Expected:

```text
positive number
```

---

## 19. Order Service gRPC Server Starts Unexpectedly

### Problem

Order Service tries to start as a gRPC server.

### Cause

Order Service should be a gRPC client to Inventory, not a gRPC server.

### Fix

In local `order-service/src/main/resources/application.yml`:

```yaml
spring:
  autoconfigure:
    exclude:
      - net.devh.boot.grpc.server.autoconfigure.GrpcServerFactoryAutoConfiguration
```

In config repo:

```yaml
grpc:
  server:
    enabled: false

  inventory:
    host: localhost
    port: 9091
```

---

## 20. Inventory gRPC Connection Fails

### Problem

Order creation fails when calling Inventory.

### Checklist

- [ ] Inventory Service is running
- [ ] Inventory gRPC server port is `9091`
- [ ] Order config points to correct host/port
- [ ] Product inventory row exists
- [ ] Available stock is enough
- [ ] Firewall is not blocking local port

Expected config:

```yaml
grpc:
  inventory:
    host: localhost
    port: 9091
```

---

## 21. PostgreSQL Database Does Not Exist

### Problem

Service fails on startup:

```text
database "auth_db" does not exist
```

or:

```text
database "order_db" does not exist
```

### Fix

Connect to Postgres:

```bash
docker exec -it ecommerce-postgres psql -U ecommerce_user -d ecommerce
```

Create DBs:

```sql
CREATE DATABASE auth_db;
CREATE DATABASE product_db;
CREATE DATABASE inventory_db;
CREATE DATABASE order_db;
```

Exit:

```sql
\q
```

---

## 22. Flyway Validation Fails

### Problem

```text
Validate failed: Migrations have failed validation
```

### Causes

- Migration file edited after it ran
- Database schema history does not match migration checksum
- Manual DB change conflicts with Flyway

### Dev Fix

For local development only:

```bash
docker compose down
docker volume rm ecommerce-platform_postgres-data
docker compose up -d postgres
```

Then recreate databases.

### Safer Fix

Create a new migration instead of editing an old one.

---

## 23. User Status Check Constraint Fails

### Problem

Delete or status update fails because DB check constraint does not allow:

```text
DELETED
```

### Fix

Add a Flyway migration:

```sql
ALTER TABLE users
DROP CONSTRAINT users_status_check;

ALTER TABLE users
ADD CONSTRAINT users_status_check
CHECK (
    status IN (
        'ACTIVE',
        'INACTIVE',
        'DELETED'
    )
);
```

---

## 24. Protected Endpoint Returns 403 Instead of 401

### Meaning

| Status | Meaning |
|---|---|
| 401 | No token or invalid token |
| 403 | Valid token but insufficient role |

Example:

```text
GET /api/v1/admin/orders with CUSTOMER token -> 403
```

This is expected.

Admin APIs require:

```text
ROLE_ADMIN
```

Make sure JWT contains:

```json
"role": "ADMIN"
```

and the JWT authority converter maps it to:

```text
ROLE_ADMIN
```

---

## 25. Resource Server Role Mapping Fails

### Problem

Admin token still receives:

```text
403 Forbidden
```

### Cause

JWT has:

```json
"role": "ADMIN"
```

but Spring Security expects:

```text
ROLE_ADMIN
```

### Fix

Use shared `JwtAuthoritiesConverter` from `common-security`:

```java
JwtAuthenticationConverter converter =
        new JwtAuthenticationConverter();

converter.setJwtGrantedAuthoritiesConverter(
        new JwtAuthoritiesConverter()
);
```

Then in service `SecurityConfig`:

```java
.oauth2ResourceServer(oauth2 ->
        oauth2.jwt(jwt ->
                jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)
        )
)
```

---

## 26. Common Commands

### Start infrastructure

```bash
docker compose up -d
```

### Stop infrastructure

```bash
docker compose down
```

### List containers

```bash
docker ps
```

### View Kafka logs

```bash
docker logs ecommerce-kafka --tail 100
```

### View Order logs

```bash
docker logs <order-container-name> --tail 100
```

If running locally with Maven, check terminal logs.

### Check Kafka port from host

```powershell
Test-NetConnection localhost -Port 29092
```

### Check Redis

```bash
docker exec -it ecommerce-redis redis-cli ping
```

Expected:

```text
PONG
```

### Check PostgreSQL

```bash
docker exec -it ecommerce-postgres pg_isready -U ecommerce_user
```

---

## 27. Recommended Startup Order

```text
1. docker compose up -d
2. create Kafka topics
3. start Config Server
4. start Auth Service
5. start Product Service
6. start Inventory Service
7. start Cart Service
8. start Order Service
9. login through Auth Service
10. test Order flow
```

---

## 28. Quick Smoke Test Checklist

- [ ] `http://localhost:8081/swagger-ui.html` loads
- [ ] `http://localhost:8082/swagger-ui.html` loads
- [ ] `http://localhost:8084/swagger-ui.html` loads
- [ ] `http://localhost:8085/swagger-ui.html` loads
- [ ] `http://localhost:8086/swagger-ui.html` loads
- [ ] Auth login returns access token
- [ ] Product public GET works without token
- [ ] Cart APIs require token
- [ ] Order APIs require token
- [ ] Admin APIs return 403 for CUSTOMER token
- [ ] Order creation reserves inventory
- [ ] Order creation publishes `order-created` event
- [ ] `/actuator/prometheus` returns metrics text