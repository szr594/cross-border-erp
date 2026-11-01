# Authentication Specification

## Scope

The authentication module provides employee login, access-token refresh, current-user lookup, and logout. Registration, password recovery, OAuth, and business-module authorization are excluded.

## Token model

- Access token: signed JWT, valid for 15 minutes and held only in frontend memory.
- Refresh token: 32 random bytes, valid for 7 days and stored in an HttpOnly cookie.
- The database stores only a SHA-256 hash of each refresh token.
- Refresh rotates the token. Reuse of an already-revoked token revokes every active refresh session for that user.
- Login failure locks the account for 15 minutes after 5 consecutive failures.

## Endpoints

| Method | Path | Authentication | Result |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | Public | JWT, user, and refresh cookie |
| POST | `/api/auth/refresh` | Refresh cookie | Rotated JWT and refresh cookie |
| GET | `/api/auth/me` | Bearer JWT | Current user |
| POST | `/api/auth/logout` | Optional refresh cookie | Revokes session and clears cookie |

## Cookie

- Name: `cross_erp_refresh`
- Path: `/api/auth`
- HttpOnly: true
- SameSite: Strict
- Secure: true in production and configurable false for local development

## Password and secret rules

- Passwords use Argon2id.
- JWT secret is supplied by `JWT_SECRET`, never source code.
- Development owner creation requires `DEMO_OWNER_PASSWORD`; no default password exists.
- Passwords, JWTs, refresh tokens, and token hashes must never be logged.

## Error envelope

```json
{
  "code": "AUTH_INVALID_CREDENTIALS",
  "message": "登录名或密码错误",
  "timestamp": "2026-10-30T10:00:00Z",
  "path": "/api/auth/login"
}
```

## Security acceptance criteria

- Missing, expired, malformed, or invalidly signed JWTs return 401.
- Authenticated users without permission return 403.
- Disabled or locked users cannot log in or refresh.
- User enumeration is prevented by identical invalid-credential responses.
- Logout is idempotent.
- The frontend retries at most one refresh for a failed request.
- Access tokens are not persisted in browser storage.
