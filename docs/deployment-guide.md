# Deployment Guide

**Status:** Local deployment instructions and production target guidance  
**Version:** 1.0  
**Updated:** 12 August 2026

## 1. Environments

| Environment | Purpose | Data/services |
| --- | --- | --- |
| Local development | Developer build and smoke testing | Docker Compose dependencies; services normally on host. |
| Stage | Integration and provider test validation | External runtime configuration and isolated dependencies. |
| Production | Customer traffic | Kubernetes target, managed dependencies, secrets, alert delivery. |

## 2. Local startup

1. Install Java 21, Maven, and Docker Desktop.
2. Start dependencies with `docker compose up -d`.
3. Verify health checks and Kafka topic initialization.
4. Start Config Server, then Gateway and dependent services using the intended profile and `CONFIG_SERVER_URL`.
5. Check `/actuator/health`, Swagger UIs, and the smoke paths in [local setup](local-setup.md).

Local dependency ports are PostgreSQL `5433`, Redis `6379`, Kafka host
listener `29092`, Tempo `3200`, Loki `3100`, Prometheus `9090`, and Grafana
`3000`. Service ports are Gateway `8080`, Config `8888`, Auth `8081`, Product
`8082`, Inventory REST/gRPC `8084/9091`, Cart `8085`, Order `8086`, and Payment
REST/gRPC `8087/9092`.

## 3. Configuration and secrets

- Keep only minimal bootstrap configuration in this repository.
- Supply database, Redis, Kafka, issuer/JWK, provider, CORS, and route settings through environment configuration.
- Supply credentials, provider keys, signing-key material, and Grafana tokens through ignored environment files or secret management; never commit them.
- Validate Config Server repository layout and `search-paths` before production reliance; the current documented layout needs correction.

## 4. Database migration and release order

Flyway runs service-owned migrations. Back up each production database and test
migrations on a production-like copy before release. Reservation-aware order
processing must be released in this order:

1. Apply Inventory reservation migration and deploy reservation-aware Inventory.
2. Verify `ReserveStock` and `ReleaseStock` accept `reservationId`.
3. Apply Order payment/outbox migrations and deploy the Order consumer/release worker.
4. Deploy Payment changes and run success, failure, duplicate, and outage smoke tests.

All migrations must remain backward-compatible during rolling deployment. Use
a forward corrective migration instead of modifying migration history.

## 5. Kubernetes production target

Each service needs a Deployment, Service, ConfigMap/secret references, resource
requests/limits, readiness/liveness probes, PodDisruptionBudget, and HPA where
appropriate. Use managed PostgreSQL, Redis, Kafka, secrets, and observability.
Network policies permit only required Gateway, gRPC, Kafka, store, and telemetry
paths. Gateway is the only public application ingress; provider webhook ingress
requires TLS and signature verification.

## 6. Release validation and rollback

Before promotion: pass unit/integration/contract tests, dependency/image scans,
migration checks, and OpenAPI/event compatibility checks. After deployment:
verify health, error rates, Kafka lag, DLQ records, payment outcome flow, and
inventory-release work. Use canary/rolling deployment with explicit rollback
criteria. Roll back binaries only when schema compatibility permits; never
discard DLQ or outbox records.

## 7. Required production readiness additions

Transactional outboxes, persistent JWK/signing-key management, active Gateway
rate-limit/circuit-breaker policy, gRPC deadlines, provider retry policy,
Kubernetes manifests, and automated CI/CD gates remain planned work.
