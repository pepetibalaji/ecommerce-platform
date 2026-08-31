# API Contract

Base path: `/api/v1/auth`. Public write endpoints should be rate-limited by IP and normalized email. Never reveal whether an email address exists during verification resend or password-reset requests.

## Registration and verification

| Method and path | Purpose | Response |
| --- | --- | --- |
| `POST /register` | Create a pending user and queue verification email. | `202 Accepted` |
| `POST /verification/resend` | Queue a new verification email. | `202 Accepted` |
| `POST /verification/confirm` | Consume a valid verification token and activate the user. | `204 No Content` |

`POST /register` request:

```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "user supplied password"
}
```

`POST /verification/confirm` request:

```json
{ "token": "one-time-token" }
```

Use a frontend confirmation page that posts the token; do not use a state-changing `GET` confirmation endpoint.

## Session APIs

| Method and path | Purpose |
| --- | --- |
| `POST /login` | Authenticate an active, verified user. |
| `POST /refresh` | Rotate a refresh token and issue a new access token. |
| `POST /logout` | Revoke the current session. |
| `GET /sessions` | List current user's active sessions. |
| `DELETE /sessions/{sessionId}` | Revoke one session. |

Login and refresh response:

```json
{
  "accessToken": "jwt",
  "refreshToken": "opaque-token",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

## Password recovery APIs

| Method and path | Purpose | Response |
| --- | --- | --- |
| `POST /password/forgot` | Queue a password-reset email if the account exists. | `202 Accepted` |
| `POST /password/reset` | Consume reset token, change password, revoke sessions. | `204 No Content` |

## Self-service user APIs

All self-service endpoints require an access token and operate only on the authenticated user.

| Method and path | Purpose |
| --- | --- |
| `GET /users/me` | Return the current user's profile, verification state, roles, and active-session summary. |
| `PATCH /users/me` | Update safe profile fields such as `displayName`. |
| `POST /users/me/email-change` | Request a confirmation email for a new address. |
| `POST /users/me/email-change/confirm` | Consume an `EMAIL_CHANGE` token and replace the verified email. |
| `POST /users/me/password` | Change password after validating the current password. |

`PATCH /users/me` request:

```json
{ "displayName": "Jane Smith" }
```

Email must not be changed directly by `PATCH`. The change request creates an `EMAIL_CHANGE` action token with `target_email`; confirmation atomically updates `email`, `email_normalized`, and `email_verified_at`. Send a security notification to both the old and new addresses.

Password change request:

```json
{
  "currentPassword": "current password",
  "newPassword": "new password"
}
```

After a password change, revoke all refresh sessions, increment `token_version`, and require login again. Do not return password hashes or security-token values from any profile endpoint.

## Admin user-management APIs

All endpoints below require an `ADMIN` role plus the corresponding permission. They are separate from self-service APIs so that authorization is explicit and auditable.

| Method and path | Required permission | Purpose |
| --- | --- | --- |
| `GET /admin/users` | `USER:READ` | Paginated search by email, status, role, and creation date. |
| `GET /admin/users/{userId}` | `USER:READ` | View one user's administrative profile. |
| `PATCH /admin/users/{userId}/status` | `USER:STATUS_WRITE` | Suspend, reactivate, or soft-delete an account. |
| `PUT /admin/users/{userId}/roles` | `USER:ROLE_WRITE` | Replace the user's assigned roles. |
| `DELETE /admin/users/{userId}/sessions` | `USER:SESSION_REVOKE` | Revoke all active refresh sessions. |

Status update request:

```json
{ "status": "SUSPENDED", "reason": "Repeated policy violations" }
```

Role update request:

```json
{ "roles": ["SELLER"] }
```

Every admin action writes an `auth_audit_events` row with the administrator as `actor_user_id` and the affected user as `subject_user_id`. Suspending, deleting, or changing roles must revoke the user's sessions and increment `token_version` so existing access tokens can be rejected at their next validation boundary.

Administrators must not directly set a user's password, mark an email verified, or change email addresses. Use the same verified, user-controlled recovery and email-change flows instead.

## JWT compatibility

Until all dependent services are migrated, issue these claims:

```json
{
  "iss": "https://auth.example.com",
  "sub": "jane@example.com",
  "userId": "uuid",
  "role": "CUSTOMER",
  "roles": ["CUSTOMER"],
  "permissions": [],
  "status": "ACTIVE"
}
```

`userId` must remain a UUID string. `role` is retained for existing Product and authorization code; `roles` is the new multi-role claim. Publish active signing keys through `/.well-known/jwks.json` and keep issuer/audience validation configured consistently in every resource service.
