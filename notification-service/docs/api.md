# Notification Service API and contracts

Base path: `/api/v1/notifications`. All endpoints require JWT authentication. A caller may read/update only the UUID matching JWT `userId`; `ADMIN` may access any user and admin routes. Role extraction supports `role`/`roles`; permissions are also parsed by security configuration.

## User endpoints

| Method/path | Behavior | Success |
| --- | --- | --- |
| `GET /users/{userId}` | Returns notification history newest-first. | `200` list of `Notification` entities |
| `GET /users/{userId}/preferences` | Returns saved preferences. Unconfigured type/channel has no row and defaults enabled at event time. | `200` list |
| `PUT /users/{userId}/preferences` | Creates or updates a preference. | `200` preference |

Preference body:

```json
{ "channel": "EMAIL", "notificationType": "PAYMENT_SUCCESSFUL", "enabled": false }
```

`notificationType` is nonblank; `channel` is enum `EMAIL` (and any enum values supported by the deployed model). A disabled matching preference causes newly consumed business-event notification work to be stored `SKIPPED`, not sent.

## Admin endpoints

| Method/path | Behavior |
| --- | --- |
| `GET /admin/failed` | Returns up to 100 FAILED notifications, oldest first. |
| `GET /admin/{notificationId}/deliveries` | Returns delivery attempts, highest attempt number first. |

The APIs expose JPA entity JSON directly, so clients should treat it as implementation-facing/admin data rather than a separately versioned public DTO contract. Actuator health/info requires no token; other actuator/OpenAPI access follows the service security policy.
