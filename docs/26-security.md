# Security

How IT-ILMS keeps accounts, data and test results trustworthy. This page gathers the rules that were spread over the architecture notes, the documentation review (§12) and the feature write-ups. Where a rule lives in code, the file is named.

Scope: one institute per deployment (no tenant ids); isolation between institutes comes from separate stacks and databases.

---

## 1. Summary

| Area | Control |
|---|---|
| Identity | BCrypt (strength 12), 30-minute access JWT, 7-day rotating refresh token, lock-out after 5 wrong passwords for 15 minutes |
| Sessions | One active login per user (replace or deny), session list per user |
| Authorization | Gateway verifies every token; every service verifies it again; `@PreAuthorize` on every endpoint plus ownership checks |
| Edge | Public-path allow-list, per-address rate limits, CORS allow-list, `/internal/**` hidden from outside |
| Secrets | Environment variables only, no defaults; a service refuses to start without them |
| Data | Refresh and reset tokens stored only as SHA-256 hashes; uploads validated by type and size; downloads access-controlled |
| Test integrity | Secure test mode, camera/face checks, remote-control tool detection, typing replay, content protection |
| Audit | Privileged actions published as events, stored by reporting-service, ADMIN can export |

---

## 2. Authentication

- **Tokens.** identity-service is the only service that signs (`JwtIssuer`). The access token carries user id, email, name, role and profile id (student id or trainer id) and nothing else, so no service calls identity to learn who is calling. Lifetimes: `JWT_ACCESS_TTL` (30 minutes), `JWT_REFRESH_TTL` (7 days).
- **Refresh rotation.** Each refresh revokes the token used and issues a new one. Presenting an already-used token revokes every session of that user, because the only explanation is a copied token.
- **Passwords.** BCrypt strength 12. Policy from `config-repo/identity-service.yml`: at least 8 characters, an upper-case letter, a lower-case letter and a digit (`PasswordPolicyValidator`). Temporary passwords are emailed and a change is forced at first sign-in; they are never stored readable.
- **Lock-out.** 5 wrong passwords lock the account for 15 minutes. The window is short and self-clearing, so an attacker cannot lock a real user out for good. The failure counter survives the exception that rejects the login (`noRollbackFor` in `AuthServiceImpl`).
- **Human check.** Optional Cloudflare Turnstile on sign-in, registration and forgot-password, verified on the server ([22-human-check-captcha.md](22-human-check-captcha.md)).
- **Other sign-in routes.** Google (the ID token is verified with Google) and a one-time WhatsApp code; both can create an account only when self-registration is on (`SELF_REGISTRATION_ENABLED`, off by default).
- **Single active login.** Default is that a new sign-in ends the old session (`REPLACE`); `DENY` refuses the new one. An already-issued access token stays valid until it expires because services verify it offline ([12-single-active-login.md](12-single-active-login.md)).
- **Password reset.** A 6-digit emailed code, single use, 2-hour life. The endpoint answers 200 whether or not the address exists, so it cannot be used to find accounts.
- **First admin.** Seeded from `BOOTSTRAP_ADMIN_EMAIL` and `BOOTSTRAP_ADMIN_PASSWORD`; there is no default password.

## 3. Authorization

- Roles: `ADMIN`, `COORDINATOR`, `TRAINER`, `STUDENT`, `PLACEMENT`, `FINANCE` (`Roles` in `common-lib`; shared expressions `STAFF`, `ACADEMIC`, `ADMIN_ONLY`, `FINANCE_DESK`, `PLACEMENT_DESK`).
- **Two checks, both mandatory.** The role (`@PreAuthorize`) says what kind of caller may use an endpoint; an ownership check in the service says whether it may touch this record. A student reads only their own records; a trainer sees only the batches they teach (checked with batch-service, failing closed when it is unreachable).
- **The gateway is not trusted by services.** It verifies the token, strips any `X-User-*` headers a client sent and adds its own, but those headers are only for logs. Each service re-verifies the JWT itself (`JwtAuthenticationFilter`).
- **Internal endpoints.** Paths under `/internal/**` return 404 at the gateway (`InternalPathGuardFilter`) and are only reachable service to service.
- **Frontend visibility is cosmetic.** `nav-items.ts` and `ProtectedRoute` shape the UI; nothing relies on them.

## 4. Edge and network

- **Public allow-list.** Only the paths in `GatewayJwtProperties.publicPaths` skip authentication: sign-in, register, refresh, forgot/reset password, captcha and social config, `/api/public/**`, public course catalog, certificate verification, the enquiry form, the LiveKit webhook, health and API docs.
- **Rate limits (Redis, per client address).** `/api/auth/**` sign-in group 15 requests/second, bursts of 40. The calls a signed-in page makes constantly (`/me`, `/refresh`, `/logout`, the two sign-in settings) have their own 50/second, bursts of 150, so a classroom behind one address is not locked out just by opening pages. Certificate verification is limited to 1/second, bursts of 10. Guessing is stopped by the lock-out and the human check, not by this limit alone.
- **CORS.** Allowed origins come from `FRONTEND_URL`, `http://localhost:3000` and the desktop app origin `itilms://app`. Every service validates CORS itself.
- **HTTPS.** Terminated at the reverse proxy in front of Nginx; see [07-deployment-guide.md](07-deployment-guide.md).
- **Resilience as protection.** Circuit breakers and a bulkhead per service keep one slow service from taking the gateway down (found and tuned by [25-load-testing.md](25-load-testing.md)).

## 5. Data

- **Secrets** (database password, `JWT_SECRET`, first admin password, LiveKit secret, Turnstile secret) come only from the environment. `deploy/docker/.env` is never committed.
- **Tokens at rest.** Refresh tokens and reset codes are stored as SHA-256 hashes; a database dump or a log line built from it holds no usable token.
- **Uploads.** file-service enforces a MIME allow-list and a per-category size ceiling (`UploadValidator`, `config-repo/file-service.yml`). Reading and deleting follow `FileAccessRules`: documents, submissions, certificates, receipts and resumes are owner-and-staff only (resumes also PLACEMENT).
- **Payments.** Outstanding fees are computed on every read from payments, never stored; a reversed payment does not count.
- **Certificates.** Issued only after every condition is checked live against the owning service; an unverifiable condition counts as not met. Public verification is by code and rate-limited.
- **Logs.** No passwords, tokens or payment data in logs; a log review is part of the release checklist.

## 6. Assessment integrity

Tests are the part of the system with a motivated attacker (the student). Server side:

- Scoring happens on the server from the stored key; the paper sent to a student contains no answer key, and answers naming another question's options are refused.
- Question pool, shuffled questions and options, and one extra-attempt allowance per student are all decided on the server.
- Coding marks come from a stored verdict plus a hash of the code; scoring never runs code at expiry.

Client side (monitoring, not prevention, and recorded as events for the trainer):

- **Secure test mode:** full screen, tab-switch warning then termination, copy block, configurable per test ([09-test-portal.md](09-test-portal.md)).
- **Camera checks:** face oval, same-face and phone detection, run in the browser (MediaPipe); events warn and are recorded.
- **Remote-control tools:** the desktop app warns about AnyDesk, TeamViewer, Getscreen and similar before a secure test, and a violation is recorded.
- **Typing replay and paste flag:** how much of a coding answer was typed versus pasted.
- **Live classes and video lessons:** optional face-visibility monitoring, watermark naming the viewer, black-out when the tab is not in view ([11](11-live-recording-and-content-protection.md), [15](15-live-class-monitoring.md), [21](21-video-lesson-monitoring.md)).

## 7. Audit

Services publish audit events; reporting-service stores them in `audit_logs`. ADMIN can filter and export them as CSV, Excel or PDF. Attendance corrections need a reason and are audited; every placement stage change is written to the stage history in the same transaction.

## 8. Known limits

- An access token cannot be withdrawn before it expires (up to 30 minutes); the refresh token can.
- Browser-side monitoring can be bypassed by someone controlling the machine. It raises the cost of cheating and leaves evidence; it does not prove honesty. OS-level screenshots and a phone camera cannot be detected by any web page.
- Audit coverage is per service; a service that does not publish an event for an action leaves no audit row for it.
- File access uses owner-and-staff rules, not a live batch or enrolment check (decision A9 in [02-documentation-review.md](02-documentation-review.md)).
- Open items L1 and L2 in the documentation review still apply.

## 9. Checklist for a new endpoint

1. `@PreAuthorize` with a `Roles` expression; no endpoint is `permitAll` unless it is in the gateway allow-list on purpose.
2. An ownership check if the data belongs to a person or a batch.
3. Validation on every request body (`@Valid`), and an upload goes through `UploadValidator`.
4. Add the gateway route and run `node deploy/scripts/check-gateway-routes.mjs`.
5. Publish an audit event for a privileged or money-related action.
6. No secret, token or personal data in logs or exception messages.
7. A unit test for the permission matrix (who is refused), not only for the happy path.
