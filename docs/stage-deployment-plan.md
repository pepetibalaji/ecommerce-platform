# E-commerce Platform — Stage Deployment Plan

## 1. Purpose and scope

Deploy the complete e-commerce microservices platform as a public **stage/demo
environment** for recruiters and limited normal-user testing. The environment
demonstrates a real distributed system: browser storefront, API Gateway, Spring
Boot microservices, Kafka events, payment flow, email flow, external data stores,
and observable telemetry.

This is deliberately **not production**. It uses single-node Kafka, a single OCI
compute host, free-tier managed services, no high availability, and limited data
retention. These limits must be stated honestly in the portfolio README and
LinkedIn post.

## 2. Target architecture

```text
Browser
  │
  ▼
Vercel frontend (HTTPS)
  │
  ▼
OCI public HTTPS endpoint
  │
  ▼
Caddy or Nginx reverse proxy
  │
  ▼
API Gateway
  │
  ├── Auth Service
  ├── Product Service ───────────────────────────► MongoDB Atlas Free
  ├── Cart Service ──────────────────────────────► Upstash Redis Free
  ├── Inventory Service ─┐
  ├── Order Service ─────┼────────────────────────► Neon PostgreSQL Free
  ├── Payment Service ───┤
  ├── Notification Service┘
  ├── Config Server ─────────────────────────────► protected configuration repo
  └── Kafka KRaft single broker

OCI VM host
  └── Grafana Alloy systemd service
        └────────────────────────────────────────► Grafana Cloud Free

Payment Service ─────────────────────────────────► Stripe Sandbox/Test Mode
Notification Service ────────────────────────────► Mailtrap Email API/SMTP Free
```

Only the frontend and Gateway are public. Every Spring service, Kafka, Config
Server, and data-store credential remains private.

## 3. Free-tier service choices

| Need | Selected service | Stage use | Important limit/decision |
| --- | --- | --- | --- |
| Compute | OCI Always Free Ampere A1 VM | Containers, Kafka, reverse proxy | Plan for at most 2 OCPUs and 12 GB RAM after trial. |
| Secrets | OCI Vault / Secret Management | Deployment/runtime secrets | Use Always Free shared/default vault; 150 secrets are included. Do not use a Virtual Private Vault. |
| PostgreSQL | Neon Free | Auth, Inventory, Order, Payment, Notification | Small demo data only; use logical service databases/schemas and no cross-service foreign keys. |
| MongoDB | MongoDB Atlas Free | Product catalogue | Product Service only; one small free cluster per project. |
| Redis | Upstash Redis Free | Cart and Auth Redis state | Use separate databases where possible, or explicit service key prefixes. Monitor command/storage limits. |
| Event broker | Apache Kafka KRaft | Internal asynchronous events | Self-hosted single broker; no ZooKeeper, no HA. |
| Observability agent | Grafana Alloy | Scrape metrics and collect logs/traces | Run directly on OCI host as a systemd service, not in a container. |
| Observability backend | Grafana Cloud Free | Dashboards, logs, metrics, traces | Use sampling/log filtering and accept free-tier retention. |
| Frontend | Vercel Hobby | Personal portfolio storefront | Personal/non-commercial showcase only; upgrade if use becomes commercial. |
| Payment | Stripe Sandbox/Test Mode | Test Checkout, failures, refunds, webhooks | No real charges or real card details. |
| Email | Mailtrap Email API/SMTP Free | Actual transactional stage emails | Verify one sender domain and apply strict auth/Gateway rate limits. |

Reference current free-tier terms before deployment because providers can change
limits:

- [OCI Always Free resources](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm)
- [Neon pricing](https://neon.com/pricing)
- [MongoDB Atlas Free Cluster](https://www.mongodb.com/docs/atlas/tutorial/deploy-free-tier-cluster/)
- [Upstash Redis pricing](https://upstash.com/pricing/redis)
- [Grafana Cloud pricing](https://grafana.com/pricing/)
- [Vercel Hobby plan](https://vercel.com/docs/plans/hobby)
- [Mailtrap pricing](https://mailtrap.io/pricing/)
- [Stripe testing](https://docs.stripe.com/testing)

## 4. OCI compute design

Use one OCI ARM VM in the tenancy home region. Run Docker Compose for all backend
containers and Kafka. Build or pull **ARM64-compatible** images.

Do not use the VM for PostgreSQL, MongoDB, Redis, Grafana, Loki, Tempo, or
Prometheus. Those workloads consume resources that are better provided by the
selected external managed tiers.

Suggested memory limits:

| Process | Memory limit |
| --- | ---: |
| Config Server | 256 MB |
| API Gateway | 384 MB |
| Auth Service | 512 MB |
| Product Service | 512 MB |
| Cart Service | 384 MB |
| Inventory Service | 384 MB |
| Order Service | 512 MB |
| Payment Service | 512 MB |
| Notification Service | 384 MB |
| Kafka KRaft broker | 1 GB |
| Caddy/Nginx | 64 MB |
| Grafana Alloy host process | 128–256 MB |
| **Planned service cap** | **about 5.3 GB** |

Leave the remaining RAM for Linux, Docker, JVM native memory, filesystem cache,
temporary spikes, and Kafka buffering.

For every JVM container, set a heap below the container limit. Typical starting
points are `-Xmx256m` in a 384 MB container and `-Xmx384m` in a 512 MB container.
Tune only after collecting real memory metrics.

Every container must have:

- explicit memory limit;
- restart policy such as `unless-stopped`;
- health check;
- structured JSON logs;
- non-root user where compatible;
- no public host port except reverse proxy/Gateway;
- pinned image tag rather than `latest`.

## 5. Network and DNS design

### Public

- Vercel frontend domain, for example `https://stage.example.com`.
- OCI reverse-proxy/Gateway domain, for example `https://api-stage.example.com`.
- Gateway HTTPS port `443` only.
- Stripe test webhook path only through Gateway:
  `POST /api/v1/payments/webhooks/stripe`.

### Private

- Gateway-to-service traffic uses Docker internal network only.
- Kafka listener is internal only; never expose Kafka ports publicly.
- Config Server is internal only; never expose port `8888` publicly.
- Actuator, Swagger, Grafana credentials, seller and admin APIs are not public
  showcase routes.

### External managed service access

- Configure Neon and MongoDB Atlas to allow the OCI VM's fixed public egress IP.
- Use TLS for Neon, Atlas, Upstash, Grafana Cloud, Stripe, and Mailtrap.
- Store all connection strings and API tokens only in OCI Vault.
- Configure exact environment-specific origins:
  - Gateway CORS origin: Vercel stage domain;
  - Stripe success/cancel return: Vercel payment-return route;
  - Auth/Notification email links: Vercel verify/reset/confirm-email-change routes.

## 6. Secrets and configuration

Use OCI Vault's default/shared Always Free vault. Do not put secrets in source
code, Docker images, Compose files, the external configuration repository, Vercel
environment variables, frontend bundles, logs, or screenshots.

Example secret inventory:

```text
NEON_DATABASE_URL
MONGODB_URI
UPSTASH_REDIS_URL
UPSTASH_REDIS_TOKEN
STRIPE_SECRET_KEY_TEST
STRIPE_WEBHOOK_SECRET_TEST
MAILTRAP_API_TOKEN
MAILTRAP_SMTP_USERNAME
MAILTRAP_SMTP_PASSWORD
GRAFANA_CLOUD_LOKI_URL
GRAFANA_CLOUD_PROMETHEUS_URL
GRAFANA_CLOUD_OTLP_ENDPOINT
GRAFANA_CLOUD_TOKEN
AUTH_ACTION_TOKEN_SIGNING_SECRET
AUTH_INTERNAL_SERVICE_TOKEN
AUTH_JWT_KEYSTORE_OR_SIGNING_SECRET
```

Recommended stage secret delivery:

1. Give the OCI VM instance principal permission to read only this stage
   compartment's secrets.
2. A root-owned deployment script reads required secrets from OCI Vault.
3. Write per-service secret files on a protected runtime path with restrictive
   permissions, then mount them read-only into the relevant containers.
4. Do not print secret values in shell output, CI logs, application logs, Docker
   inspection output, or Grafana.
5. Rotate secrets by updating OCI Vault then performing controlled redeployment.

Config Server is internal and must be protected. The external configuration
repository contains non-secret configuration and secret references/placeholders
only. Stage clients must fail fast when required configuration or secrets are
missing rather than silently starting with unsafe defaults.

## 7. Kafka stage design

Run open-source Apache Kafka in **single-node KRaft mode**.

- No ZooKeeper.
- One broker.
- One partition per low-volume topic initially.
- Replication factor `1` and `min.insync.replicas=1`.
- Disable automatic topic creation; provision documented topics during deployment.
- Keep normal topic retention at 24–72 hours for the demo.
- Reserve 10–20 GB local disk for Kafka data and monitor disk usage.
- Set Kafka heap around 768 MB–1 GB initially.
- Keep broker and controller listeners private to the Docker network.

The number of application topics is not the practical issue for this stage setup.
Twenty to forty low-volume topics is reasonable. The real constraints are message
volume, consumer health, disk, CPU, and RAM.

Monitor consumer lag. Lag is expected during a service restart or an external
provider failure, but it must clear after recovery. Test Kafka restart and consumer
restart recovery before sharing the URL.

## 8. Grafana Alloy and observability

Install Grafana Alloy directly on the OCI VM as a systemd service. It is free,
open source, and should be colocated with workloads so it can read host/container
logs and scrape private service metrics.

Alloy responsibilities:

- scrape Spring Boot `/actuator/prometheus` endpoints over the private Docker
  network;
- collect Docker/container logs from the OCI host;
- forward OpenTelemetry traces if configured;
- add environment, service, version, and host labels;
- redact secrets, tokens, action links, cookies, passwords, payment data, and
  addresses;
- apply log filtering/sampling to remain inside Grafana Cloud Free limits.

Grafana Cloud is the external observability backend. Do not run Grafana, Loki,
Tempo, or Prometheus locally for this stage plan.

Create dashboards for:

- Gateway request rate, latency, `401`, `403`, `429`, `5xx`, and CORS failures;
- JVM heap, CPU, container restart count, and OCI disk/RAM;
- Kafka producer errors, consumer lag, and topic disk usage;
- Order/outbox and inventory release backlog;
- payment webhook/outcome/refund failures;
- notification pending/failed delivery counts;
- dependency failure rates for Neon, Atlas, Upstash, Mailtrap, and Stripe.

## 9. Email strategy

Use one of these Mailtrap options intentionally:

| Goal | Mailtrap option | Result |
| --- | --- | --- |
| Developer-only verification | Email Sandbox | Emails are captured in Mailtrap; no normal user receives email. |
| Recruiter/normal-user verification | Email API/SMTP Free | Actual transactional email reaches approved recipients; verify sender domain and enforce rate limits. |

For the public recruiter demo, use **Mailtrap Email API/SMTP Free** with a verified
sender domain. Configure strict Gateway/Auth limits on registration, resend
verification, password reset, and email change to prevent spam abuse.

The frontend must say “Check your inbox if eligible,” never claim that email was
delivered, and never reveal whether an account exists.

## 10. Stripe stage strategy

Use Stripe Sandbox/Test Mode only.

- Use test publishable and secret keys from OCI Vault.
- Create a test webhook to the public Gateway route.
- Store the test webhook signing secret in OCI Vault.
- Gateway permits this exact webhook path without JWT; Payment Service verifies
  Stripe's signature.
- Use Stripe test cards to demonstrate success, failure, refund, and authentication
  paths.
- Never use real cards or live API keys.
- Redirect users back to the Vercel route:
  `/payment/return?orderId=&paymentId=`.
- The frontend verifies outcome by polling authenticated Order and Payment APIs;
  provider redirect alone is not payment proof.

## 11. Gateway and frontend contract

Frontend environment configuration is public-only:

```env
VITE_API_BASE_URL=https://api-stage.example.com
VITE_APP_NAME=E-commerce Platform Stage
VITE_PUBLIC_FEATURE_FLAGS=...
```

Never put backend secrets, Config Server URL, service ports, database connection
strings, Vault references, or provider credentials in Vercel or frontend code.

Gateway requirements before public release:

- public catalogue read routes;
- guest-cart routes;
- public Auth verification/reset/email-change confirmation routes;
- public Stripe webhook route only;
- `Idempotency-Key` allowed in CORS headers;
- exact Vercel origin with credentialed CORS;
- protected customer/seller/admin routes;
- rate limits for Auth, cart mutations, checkout, cancellation, and webhooks;
- safe `429`, `503`, and `504` response contract;
- no direct browser access to individual service ports.

## 12. Required hardening before sharing publicly

Complete or clearly scope the backend tickets for:

1. Auth Service.
2. Product Service.
3. Cart Service.
4. Inventory Service.
5. Order Service.
6. Payment Service.
7. Notification Service.
8. Gateway Service.
9. Config Server.
10. Platform stage/production readiness.

For a recruiter demo, the minimum non-negotiable items are Gateway public-route/
CORS fixes, Gateway rate limits, Config Server/secret protection, Stripe webhook
verification, email-link security, secure token handling, and no exposed internal
ports. Do not claim production readiness until all ticket acceptance criteria are
implemented and verified.

## 13. Deployment order

1. Create OCI compartment, network rules, VM, DNS, TLS certificate, and OCI Vault.
2. Create Neon, Atlas, Upstash, Grafana Cloud, Mailtrap, Stripe test, and Vercel
   accounts/projects.
3. Configure fixed OCI egress IP allowlists in Neon and Atlas.
4. Prepare protected stage configuration and Vault secret references.
5. Build ARM64 backend images and publish versioned tags.
6. Install Docker Compose, Caddy/Nginx, and Grafana Alloy on OCI VM.
7. Start Kafka KRaft and verify broker health/topics.
8. Start Config Server, then Auth, Gateway, Product, Inventory, Cart, Order,
   Payment, and Notification services using health checks.
9. Deploy frontend to Vercel with only public environment values.
10. Configure Gateway CORS, Vercel origin, Stripe return/webhook URLs, and email
    link URLs.
11. Create Grafana dashboards and alerts.
12. Run the stage smoke test suite before sharing the public URL.

## 14. Stage smoke tests

Run and record evidence for:

1. Anonymous catalogue list/detail through Gateway.
2. Guest cart creation, refresh, quantity change, and merge after login.
3. Registration, Mailtrap email receipt, verification link, and sign-in.
4. Password reset and email-change link flows.
5. Cart to checkout with required `Idempotency-Key` CORS preflight.
6. Order creation and durable `order-created` event.
7. Stripe test success, failure, cancellation, expiry, and refund where supported.
8. Verified Stripe webhook through Gateway and Order state update.
9. Notification delivery and safe resend behavior.
10. Service restart, Kafka restart, and consumer-lag recovery.
11. Rate-limit response and `Retry-After` behavior.
12. Unauthorized customer/seller/admin route protection.
13. Grafana logs, metrics, and traces for an end-to-end checkout.

## 15. Cost and safety controls

- Check OCI service limits and Always Free labels before creating resources.
- Create OCI budget/usage alerts even when planning zero spend.
- Monitor Neon, Atlas, Upstash, Grafana, Mailtrap, Stripe, and Vercel usage pages.
- Add a low-volume demo data set; do not seed large images or event payloads.
- Set short Kafka retention and external free-tier log retention expectations.
- Set Docker log rotation and local disk alerts.
- Keep an emergency procedure: disable public frontend, block Gateway ingress, rotate
  Vault secrets, and inspect logs by trace ID.

## 16. Known stage limitations to disclose

```text
- One OCI VM and one Kafka broker; no high availability.
- Kafka replication factor is one.
- Managed data stores use free-tier quotas and public-network TLS access.
- No production SLA, multi-region failover, or disaster-recovery guarantee.
- Grafana retention is limited by free tier.
- Stripe uses test payments only; no real money moves.
- Mailtrap capacity and delivery are free-tier limited.
- The environment is for portfolio demonstration and controlled testing.
```

## 17. Recruiter showcase package

Publish or link:

1. Live Vercel stage URL.
2. GitHub repository and architecture diagram.
3. Short demo video: browse → guest cart → login → checkout → Stripe test payment
   → order confirmation → email notification.
4. Grafana dashboard screenshot showing metrics, trace, and logs for the same flow.
5. README section explaining microservice boundaries, Kafka events, security choices,
   observability, and stage limitations.
6. A safe demo account; never publish administrator credentials or secrets.

## 18. Definition of done

The stage environment is ready to share when:

- frontend, Gateway, and custom domains are HTTPS;
- only Gateway is public on the backend;
- secrets come from OCI Vault and are absent from Git/logs/browser bundles;
- all services are healthy with memory limits and restart policies;
- Kafka events and consumer recovery are verified;
- Stripe test webhook and Mailtrap email workflows work;
- Vercel/Gateway/email/payment origins match exactly;
- Grafana provides usable metrics, logs, and traces;
- core smoke tests pass;
- stage limitations are documented honestly.
