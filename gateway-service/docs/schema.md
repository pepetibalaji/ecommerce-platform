# Gateway Service data model

Gateway owns no relational database, Mongo collection, Kafka topic, or domain schema.

When externally configured routes enable Redis rate limiting, Redis stores ephemeral counter/bucket data keyed from one of the configured `KeyResolver` beans:

| Resolver | Key priority |
| --- | --- |
| `userOrIpKeyResolver` (primary) | `user:{userId}` → `sub:{subject}` → `principal:{name}` → `ip:{address}` |
| `ipKeyResolver` | `ip:{address}` |

Key names, TTLs, replenishment rates, burst capacity, and Redis storage format are owned by Spring Cloud Gateway's configured rate-limiter filter and the environment configuration. Do not use these ephemeral counters as identity/audit records.
