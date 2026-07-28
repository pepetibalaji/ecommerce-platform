# Local observability stack

This folder configures the local development observability stack started by
`docker compose up`:

```text
Spring Boot services (host) --OTLP--> OpenTelemetry Collector --OTLP--> Tempo
Spring Boot services (host) --/actuator/prometheus--> Prometheus <--remote write-- Tempo metrics generator
Spring Boot services (host) --JSON files--> Alloy --> Loki
Grafana --> Prometheus, Tempo, and Loki
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
file under `<repository>/logs`; Alloy reads that directory into Loki.

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
