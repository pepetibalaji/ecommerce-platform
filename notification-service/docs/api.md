# API Contract

| Endpoint | Purpose |
| --- | --- |
| `GET /api/v1/notifications/users/{userId}` | Read a user's notifications. |
| `GET`, `PUT /api/v1/notifications/users/{userId}/preferences` | Read or update delivery preferences. |
| `GET /api/v1/notifications/admin/failed` | ADMIN failed-delivery view. |
| `GET /api/v1/notifications/admin/{notificationId}/deliveries` | ADMIN delivery history. |

Authorization is enforced by the service security configuration; callers must not be able to query
another user's notifications except through authorized administration.
