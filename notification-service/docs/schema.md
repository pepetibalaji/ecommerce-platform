# Notification Service data model

PostgreSQL/Flyway owns notification storage.

| Entity/table | Purpose |
| --- | --- |
| `notifications` | Event ID, recipient user, channel/type, safe payload, PENDING/SENT/FAILED/SKIPPED status, creation/send time. |
| `notification_deliveries` | One provider attempt with count, provider message ID, status/error, next attempt schedule. |
| `notification_preferences` | Per user/channel/notification-type enabled switch. |
| `processed_events` | Unique Kafka event IDs for idempotent business-event handling. |
| `notification_recipients` | Local user ID, email, and active status maintained by contact events. |

Notification payloads are designed to retain only safe business fields. Auth action notifications store action metadata/delivery email; the raw action URL/token is created on a detached in-memory copy immediately before provider send and is not flushed back to the notification record.
