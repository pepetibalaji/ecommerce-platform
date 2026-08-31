# Config Server

## What this service is

Config Server is the configuration entry point for the platform. It runs on port `8888` and reads environment-specific YAML files from the separate `ecommerce-config-repo`. It does not own commerce business data.

## Technology

- Java 21, Spring Boot, Spring Cloud Config Server
- Git-backed configuration repository
- Spring Boot Actuator and structured logs

## End-to-end flow

```text
Service starts with profile=dev/stage/prod
  -> requests /<service-name>/<profile> from Config Server
  -> Config Server reads ecommerce-config-repo
  -> service receives database, Kafka, security, and provider configuration
  -> service starts with that environment configuration
```

## Run locally

```bash
cd config-server
mvn spring-boot:run
```

Verify: `http://localhost:8888/auth-service/dev`.

## Required configuration

- Git/config-repository location and access.
- Environment profile files in `ecommerce-config-repo/dev`, `stage`, and `prod`.
- Secrets supplied by local ignored environment files or the deployment secret manager.

## Current and next work

Current: serves central service configuration. Next: protected Git access, refresh strategy, and stage/prod deployment hardening.
