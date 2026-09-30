# Single active login per user

Enforced in identity-service at sign-in; the frontend only explains what happened.

- **Session record.** A `refresh_tokens` row is the session (V2 migration adds `session_id`, `device_label`, `session_started_at`, `last_activity_at`, `revoke_reason`). `session_id` survives token rotation, so one sign-in on one device is one session.
- **Policy** (`itilms.security.sessions.*`): `single-session-roles` (default all roles; empty list = off), `conflict-policy` `REPLACE` (default: new login ends the old session) or `DENY` (new login refused while another session was active within `idle-timeout`, default 30 min).
- **Replay safety.** A device signed out by a newer login that presents its old refresh token gets "signed in on another device". This is deliberately *not* treated as token theft, so the new session survives.
- **Limit.** An already-issued access token stays valid until it expires (`JWT_ACCESS_TTL`, 30 min by default) because other services verify it without calling identity. The old device cannot refresh, so it is out at the latest then. Lower the TTL to narrow the window.
- **Last activity** is refreshed on every token refresh (~every 24 min of use), not on each API call.
- **Endpoints.** `GET /api/auth/sessions` (own), `GET /api/auth/sessions/user/{id}` (ADMIN): login time, last activity, logout time, status `ACTIVE | LOGGED_OUT | REPLACED | EXPIRED`. Audit event `SESSION_REPLACED`.
