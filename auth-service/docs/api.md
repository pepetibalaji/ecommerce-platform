# API Contract

Base paths: Auth endpoints use `/api/v1/auth`; self-service endpoints use `/api/v1/users`; admin endpoints use `/api/v1/admin/users`. Public write endpoints should be rate-limited by IP and normalized email at the gateway/edge until Auth has an in-service control. Never reveal whether an email address exists during verification resend or password-reset requests.

## Registration and verification

| Method and path | Purpose | Response |
| --- | --- | --- |
| `POST /api/v1/auth/register` | Create a pending user and queue verification email. | `202 Accepted` |
| `POST /api/v1/auth/verification/resend` | Queue a new verification email. | `202 Accepted` |
| `POST /api/v1/auth/verification/confirm` | Consume a valid verification token and activate the user. | `204 No Content` |

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
| `POST /api/v1/auth/login` | Authenticate an active, verified user, set an HTTP-only refresh cookie, and return an access token. |
| `POST /api/v1/auth/refresh` | Rotate the HTTP-only refresh cookie and issue a new access token. No refresh secret is accepted in JSON. |
| `POST /api/v1/auth/logout` | Blacklist the presented access JWT, revoke the refresh session from its cookie, and expire that cookie. |
| `GET /api/v1/users/me/sessions` | List current user's active sessions. |
| `DELETE /api/v1/users/me/sessions/{sessionId}` | Revoke one session. |

Login and refresh response. The opaque refresh secret is delivered only in a `Secure` (outside local development), `HttpOnly`, `SameSite=Lax` cookie scoped to `/api/v1/auth`; it is never included in this JSON response.

```json
{
  "accessToken": "jwt",
  "tokenType": "Bearer",
  "expiresInSeconds": 1800,
  "user": { "id": "uuid", "name": "Jane Doe", "email": "jane@example.com", "role": "CUSTOMER", "status": "ACTIVE" }
}
```

## Password recovery APIs

| Method and path | Purpose | Response |
| --- | --- | --- |
| `POST /api/v1/auth/password/forgot` | Queue a password-reset email for a matching active, verified account without revealing eligibility. | `202 Accepted` |
| `POST /api/v1/auth/password/reset` | Consume reset token, change password, revoke sessions. | `204 No Content` |

## Self-service user APIs

All self-service endpoints require an access token and operate only on the authenticated user.

| Method and path | Purpose |
| --- | --- |
| `GET /api/v1/users/me` | Return the current user's profile, role, and status. |
| `PUT /api/v1/users/me` | Update safe profile fields such as `name`. |
| `DELETE /api/v1/users/me` | Soft-delete the authenticated user and revoke refresh sessions. |
| `POST /api/v1/users/me/email-change` | Request a confirmation email for a new address. |
| `POST /api/v1/auth/email-change/confirm` | Publicly consume a one-time `EMAIL_CHANGE` token and replace the verified email; all sessions are revoked. |
| `POST /api/v1/users/me/password` | Change password after validating the current password. |

`PUT /users/me` request:

```json
{ "name": "Jane Smith" }
```

Email must not be changed directly by `PUT`. The change request creates an `EMAIL_CHANGE` action token with `target_email`; confirmation atomically updates `email`, `email_normalized`, and `email_verified_at`, then publishes the contact-directory update.

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
| `GET /api/v1/admin/users` | `USER:READ` | Paginated user list. |
| `GET /api/v1/admin/users/{userId}` | `USER:READ` | View one user's administrative profile. |
| `PATCH /api/v1/admin/users/{userId}/status` | `USER:STATUS_WRITE` | Suspend, reactivate a verified suspended account, or soft-delete an account. |
| `PUT /api/v1/admin/users/{userId}/roles` | `USER:ROLE_WRITE` | Replace the user's assigned roles. |
| `DELETE /api/v1/admin/users/{userId}/sessions` | `USER:SESSION_REVOKE` | Revoke all active refresh sessions. |

Status update request:

```json
{ "status": "SUSPENDED" }
```

Role update request:

```json
{ "roles": ["SELLER"] }
```

Every admin action writes an `auth_audit_events` row with the administrator as `actor_user_id` and the affected user as `subject_user_id`. Suspending, deleting, or changing roles revokes refresh sessions and increments `token_version`. Existing JWTs remain valid until expiry unless each resource server adopts token-version or revocation checking.

Administrators must not directly set a user's password, mark an email verified, or change email addresses. Use the same verified, user-controlled recovery and email-change flows instead.

## Internal seller eligibility

`GET /internal/auth/sellers/{sellerId}/eligibility` is a service-to-service contract used by Product Service. Send the shared service credential in `X-Internal-Auth`; a browser access token does not authorize this call. Configure Auth with `auth.internal.service-token` / `AUTH_INTERNAL_SERVICE_TOKEN`, with the matching secret injected into Product. Stage and production refuse to start if the Auth secret is empty or unresolved. Internal paths must not be routed through the public gateway.

Successful responses contain only:

```json
{ "eligible": true }
```

Eligibility requires an existing, active, email-verified account with the `SELLER` role and no deletion timestamp. Additional roles do not disqualify a seller. Missing, unverified, suspended, deleted, or non-seller accounts return HTTP 200 with `eligible: false`; no profile information is disclosed. Missing or incorrect service credentials return HTTP 403 before account lookup. Product must fail closed when Auth is unavailable or the response cannot establish eligibility.

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
  "status": "ACTIVE",
  "email_verified": true,
  "tokenVersion": 4
}
```

`userId` must remain a UUID string. `role` is retained for legacy resource-service consumers; `roles` is the new multi-role claim. Publish active signing keys through `/oauth2/jwks` and keep issuer/audience validation configured consistently in every resource service.
