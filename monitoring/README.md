# Local observability stack

This folder configures the local development observability stack started by
`docker compose up -d`:

```text
Spring Boot services (host) --OTLP--> OpenTelemetry Collector --> Tempo
Spring Boot services (host) --/actuator/prometheus--> Prometheus --> Grafana
Spring Boot services (host) --JSON files--> Loki --> Grafana
```

## Service prerequisites

The Spring Boot services run on the host in this setup. Each service must expose
`/actuator/prometheus` and send traces to the collector:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
  tracing:
    sampling:
      probability: 1.0
```

The application name must match the Prometheus `service` label (for example,
`gateway-service`). This is normally satisfied by `spring.application.name`.

Services write JSON logs to `../logs/<service-name>.json` by default, relative
to the service module. That resolves to the repository-level `logs` directory
when a service is started from its module. If you launch a JAR from somewhere
else, set `OBSERVABILITY_LOG_FILE` to the absolute path of the corresponding
file under `<repository>/logs`; Alloy reads that directory and forwards it to
Grafana Cloud Logs.

## Hosted logging

Run Alloy alongside the services and mount the same log directory read-only at
`/var/log/ecommerce`. Configure these values as deployment secrets/environment
variables; never commit the Grafana Cloud token:

```text
GRAFANA_CLOUD_LOKI_URL=https://logs-<region>.grafana.net/loki/api/v1/push
GRAFANA_CLOUD_LOKI_USERNAME=<Loki instance ID>
GRAFANA_CLOUD_TOKEN=<access-policy token with Logs:Write>
OBSERVABILITY_ENVIRONMENT=production
OBSERVABILITY_CLUSTER=ecommerce-production
```

For staging, run only Alloy with the stage environment file from the
configuration repository:

```powershell
docker compose --env-file ../ecommerce-config-repo/.env.stage --profile stage-observability up -d alloy
```

Each service retains at most 10 MB locally by default (a 5 MB active file plus
up to one rolled file). This is only a short buffer for Alloy; Grafana Cloud
Free retains ingested logs for 14 days, then removes them automatically. You
can lower the local limits with `OBSERVABILITY_LOG_MAX_FILE_SIZE`,
`OBSERVABILITY_LOG_MAX_HISTORY`, and `OBSERVABILITY_LOG_TOTAL_SIZE_CAP`.

## Naming and correlation standard

Use these exact names everywhere. They are deliberately lowercase for log JSON
fields and use standard HTTP header casing at the API boundary.

| Purpose | Canonical name | Where it belongs |
|---|---|---|
| Distributed request identifier | `traceId` | MDC/log JSON and `X-Trace-Id` response header |
| Current operation identifier | `spanId` | MDC/log JSON and `X-Span-Id` response header |
| Service identity | `service.name` | trace resource attribute; Grafana/Loki label is `service` |
| Error category | `error.type` | trace attribute, e.g. `IllegalStateException` |
| Human-readable failure | `error.message` | trace/log field; do not put it in a Loki label |
| HTTP route/status | `http.route`, `http.response.status_code` | trace and metric attributes |

Only low-cardinality dimensions are Loki or Prometheus labels: `service`,
`level`, `environment`, and metric status/route labels provided by Micrometer.
Never use `traceId`, `spanId`, user IDs, order IDs, messages, or exception text
as labels. Query them from structured log content instead.

For application failures, log the exception object as the final logger argument
so Loki receives the stack trace. Expected client mistakes (4xx) should be
logged at `WARN` without a stack trace; unexpected server failures (5xx) should
be `ERROR` with a stack trace.

## Validate the stack

After starting the services and Compose stack, open:

- Prometheus targets: `http://localhost:9090/targets`
- Grafana: `http://localhost:3000`
- Tempo readiness: `http://localhost:3200/ready`
- Loki readiness: `http://localhost:3100/ready`

All application targets are intentionally addressed as `host.docker.internal`.
That is appropriate for Docker Desktop when the Spring services run on the host.
If the services are later added to Compose, replace those targets with the
Compose service names and ports.

## Payment outcome monitoring

Order Service publishes low-cardinality payment outcome counters to Prometheus.
The provisioned **Ecommerce / Order Payment Outcomes** Grafana dashboard shows:

- listener deliveries and order updates by `success` or `failure`
- duplicate and late events ignored by the idempotent consumer
- retry attempts and DLQ recovery attempts
- Kafka lag for consumer group `order-service-payment-outcomes`
- durable inventory-release commands queued, completed, and retried by reason

Kafka Exporter reads consumer-group offsets from the local Kafka cluster, and
Prometheus scrapes it as `kafka-exporter`. The dashboard and alert rules focus
on `payment-success` and `payment-failed` topics for the Order Service consumer
group.

The alert rules include a DLQ event alert, a retry-volume warning, a
consumer-lag warning, and an inventory-release retry warning. Local Prometheus evaluates these rules; production alert
delivery (email, Slack, PagerDuty, or Grafana Cloud alerting) must be configured
with the environment-specific notification credentials.

Verify the final metric names after starting Order Service:

```text
http://localhost:8086/actuator/prometheus
```

Search for `order_payment_outcome` and `order_inventory_release` and use the
exposed names in any custom dashboard queries.
