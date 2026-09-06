# Gateway Service documentation

Gateway Service is the reactive edge entry point. It owns route dispatch, gateway-level JWT authorization, CORS, optional Redis rate-limit keys, dependency fallback responses, and edge observability. It owns no business data or business workflow.

| Document | Purpose |
| --- | --- |
| [API and contracts](api.md) | Edge authorization, public paths, CORS, fallback, and forwarding contract. |
| [High-level design](hld.md) | Boundary, downstream routing model, and dependencies. |
| [Low-level design](lld.md) | Security matcher order, JWT roles, rate-limit keys, error handlers. |
| [Data model](schema.md) | No owned database; Redis rate-limit usage. |
| [Events and operations](events-and-operations.md) | Route configuration, timeouts, gateway actuator, and failure handling. |
| [Current implementation](current-implementation.md) | Actual implementation and limitations. |
