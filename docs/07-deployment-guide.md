# Deployment Guide

[05-local-setup.md](05-local-setup.md) covers running the stack on a developer's own machine with `docker compose`. This document covers the parts a real deployment adds on top of that: what changes between environments, what production needs that local development skips over, and how to operate the stack once it is up. Read 05 first if you have not stood the stack up before — the commands here assume you have.

There is no Kubernetes manifest yet (Doc departure D5 — Compose first, Kubernetes later). Everything below still applies to a Kubernetes deployment; only the packaging of "one container per service with these env vars and this health check" changes.

---

## 1. What ships

Eighteen containers: 5 infrastructure (Postgres, Kafka, Redis, LiveKit, Mailpit\*), 3 platform (discovery-server, config-server, api-gateway), 12 business services, 1 frontend.

\* Mailpit is a **local development convenience only** — see §5.

Each business service is:
- **One image, one Dockerfile** (`deploy/docker/Dockerfile`, shared by every Spring service): `eclipse-temurin:21-jre-alpine`, copies a pre-built jar, runs as a non-root user, sizes its heap from the container's own memory limit (`-XX:MaxRAMPercentage=70`), and exits on OOM rather than thrashing.
- **Built from a jar Maven already produced**, not compiled inside Docker:
  ```
  mvn -DskipTests package        # from the repository root, before any image build
  docker compose up -d --build
  ```
  This is deliberate: the jar that passed `mvn clean test` in CI is the one that ships, and a second in-container compile could in principle produce a different artifact.
- **Configured from `config-repo/`**, served by config-server, with every secret coming from an environment variable that has no default — a service refuses to start rather than silently running with a placeholder.

## 2. Environments

Locally, `deploy/docker/.env` (git-ignored, copied from `.env.example`) holds every secret and every environment-specific value: database password, `JWT_SECRET`, the bootstrap admin's password, LiveKit's API secret, mail settings, and network binding.

A second environment (staging, production) needs its **own** `.env` with its own secret values — never the same `JWT_SECRET` or database password as local development, and never committed. The variables that must change between environments:

| Variable | Local default | Production concern |
|---|---|---|
| `JWT_SECRET` | dev-only, in `.env` | Rotate independently per environment. Changing it logs out every session everywhere at once — coordinate the change |
| `POSTGRES_PASSWORD` | dev-only | A managed database's own credential, not a container env var, if using managed Postgres instead of the `postgres` container |
| `BOOTSTRAP_ADMIN_PASSWORD` | dev-only | Change immediately after first sign-in in any real environment; the bootstrap flow only ever runs once (no admin exists yet) |
| `LIVEKIT_API_SECRET`, `LIVEKIT_WS_URL` | `ws://localhost:7880` | Must be a URL browsers on the public internet can reach, normally `wss://` behind a reverse proxy (see §4) |
| `FRONTEND_URL`, `GATEWAY_URL` | localhost | The gateway's CORS allow-list and the frontend's build-time API base both derive from these — see §3 |
| `BIND_ADDRESS` | `127.0.0.1` | Stays `127.0.0.1` even in production if everything sits behind a reverse proxy on the same host; only becomes `0.0.0.0` if a container port must be reached directly (e.g. multi-host, or LiveKit's UDP media port — see §4) |
| `STORAGE_BACKEND` | `local` (disk volume) | Switch to `minio` for anything beyond one host — see §6 |
| `MAIL_ENABLED`, `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_TLS` | Mailpit | A real SMTP provider — see §5 |

Nothing in the repository ever contains a working secret for any environment; `config-repo/*.yml` reference environment variable names only.

## 3. Frontend: the one build-time coupling

`VITE_API_BASE_URL` is baked into the JS bundle at **build time** (`frontend/Dockerfile`'s `docker build --build-arg`), not read at container start. It must be the gateway address a **browser** can reach — not a Docker network name like `api-gateway`, which only resolves between containers.

Consequence: **changing the gateway's public URL means rebuilding the frontend image**, not just restarting it. There is no way around this without moving to runtime-injected config (not built here). Plan the gateway's public hostname before the first production build.

## 4. Putting it behind a reverse proxy (HTTPS)

Doc §12 requires HTTPS in production; nothing in this repository terminates TLS — that is a reverse proxy's job (Nginx, Caddy, a cloud load balancer), sitting in front of:
- **api-gateway** (`8080`) — proxy `https://api.yourdomain/` → `gateway:8080`. WebSocket upgrade is not needed here (LiveKit handles its own transport).
- **frontend** (`8080` in-container, published as `5173` locally) — proxy `https://app.yourdomain/` → `frontend:8080`.
- **LiveKit** (`7880` signalling, `7881` TCP fallback, `7882/udp` media) needs `wss://` from the browser's perspective. LiveKit's own docs cover TLS termination for its signalling port; the UDP media port (`7882`) generally cannot go through a typical HTTP(S) reverse proxy and needs either a direct reachable UDP port or LiveKit's TURN/relay configuration for restrictive networks. Set `LIVEKIT_NODE_IP` to the address clients will actually reach the media port on.

Browsers refuse camera/microphone access on any origin except `localhost` served over plain HTTP — live classes will not work in production without HTTPS on the frontend's origin, independent of anything above.

Every service also validates CORS itself (`FRONTEND_URL`), so update that variable everywhere it appears (`x-service-env` in compose, or your environment's equivalent) when the frontend's real origin is decided.

## 5. Email

Real email is used for **two things only**: the temporary password sent when an administrator creates an account (or resets someone's password), and the one-time code sent for "forgot password". Every other notification (fees, results, announcements, job postings) stays in-app. `MAIL_NOTIFICATIONS_ENABLED` (default `false`) would turn ordinary notification email on as well; leave it off to keep to the two cases above.

How it behaves:
- Password and reset-code emails are sent straight away and never queued, because the queue is a table and a secret must not be stored. If the send fails, the event is redelivered and sent again.
- Ordinary notification email, when switched on, goes through the `email_outbox` table and is retried with a delay.
- Locally, `mailpit` catches every email so nothing is ever really sent (`MAIL_ENABLED=true` pointed at `mailpit:1025`).

To send for real, set in `deploy/docker/.env` and restart `notification-service`:
- `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_TLS=true`, and `MAIL_FROM` (and `MAIL_FROM_NAME`, the display name).
- **Gmail** (free, about 500 a day): `smtp.gmail.com`, port 587; turn on 2-Step Verification and create an **App password**; use it as `MAIL_PASSWORD` and the same Gmail address as `MAIL_USERNAME` and `MAIL_FROM`.
- **Brevo** (free, 300 a day): `smtp-relay.brevo.com`, port 587; use the SMTP login and SMTP key and a verified sender.
- Make sure the user has an e-mail address on the account; a user with none gets nothing, and the log says so.
- **Do not deploy `mailpit` anywhere but a developer's own machine or CI.** It is an open, unauthenticated mail catcher with no purpose once real email is configured.

Mail from a free provider can land in spam. For a real institute, send from your own domain with SPF and DKIM set up (Brevo and other providers walk you through it).

## 6. File storage

`STORAGE_BACKEND=local` (the default) writes into the `file-storage` named volume, mounted only into `file-service`'s own container. That is fine for one host; it does not survive that host disappearing, and no other container can read it.

For anything beyond a single machine, switch to MinIO (Doc §17, the production choice — already wired in code, just not part of `docker-compose.yml`):
```
STORAGE_BACKEND=minio
MINIO_ENDPOINT=http://minio:9000
MINIO_ACCESS_KEY=...
MINIO_SECRET_KEY=...
MINIO_BUCKET=itilms
```
This has not yet been exercised end-to-end in this project (see the "still not done" list below) — validate an upload → download → delete cycle against a real MinIO instance before relying on it.

## 7. Resource sizing

Each business service is capped at `512M` (`deploy: resources: limits: memory:` in compose), except:
- **notification-service: 640M** — Thymeleaf templating plus the outbox job and a mail client sit close to the 512M ceiling under load (measured at ~470M during the smoke test); raised after hitting this in practice.
- **api-gateway, identity-service, assessment-service, batch-service, course-service and liveclass-service: 768M** (changed 2026-10-31). They carry the load when a class signs in and takes a test together; batch-service had been sitting at 495M of 512M. Raise further if `docker stats` shows them near the limit.
- **frontend: 128M** — static Nginx only.

Platform services (gateway, config-server, discovery-server) are 512M each. The whole stack is roughly **4.5GB** and 18 containers; Docker Desktop needs at least 6GB allocated, or containers get OOM-killed rather than failing with a clear error. `JAVA_OPTS`'s `-XX:MaxRAMPercentage=65` (70 in the shared Dockerfile default) means the JVM heap is sized from *the container's* limit — raising a service's memory limit without also raising `JAVA_OPTS` if you override it wastes the extra memory outside the heap.

Kafka runs as `apache/kafka-native` (GraalVM native build) specifically for fast startup and low idle memory in development; production deployments should use the JVM image (`apache/kafka`) with the same broker settings, which is more battle-tested at real throughput.

## 8. Database

One PostgreSQL instance hosts all twelve databases locally (`postgres:17-alpine`, one container, `postgres/init/` creates each database on first start). Each service still only ever connects to and migrates its own database (Flyway, `ddl-auto: validate` — the JPA layer never alters schema at runtime). Splitting the databases across separate Postgres instances, or moving to a managed Postgres service, requires no code change: only `DB_HOST`/`DB_PORT`/credentials per service.

**Backup:** `deploy/scripts/backup-databases.sh` dumps every `itilms_*` database from the Postgres container into `~/backups/YYYY-MM-DD/` (gzip, each file tested and checked for size; the script exits non-zero if any database failed). It keeps 14 daily folders and the first of each month for 12 months (`KEEP_DAILY`, `KEEP_MONTHLY`). It is run daily from cron on the server: `30 2 * * * /home/azureuser/ITLMS/deploy/scripts/backup-databases.sh >> /home/azureuser/backups/cron.log 2>&1`. Set `OFFSITE_RCLONE_REMOTE=remote:folder` to copy each day to another place with rclone: **a backup on the same disk is lost with the disk**, so an off-server copy is part of the job, not an extra. Uploaded files (the file-service volume) are not in these dumps. To restore one database: create it empty, then `gunzip -c itilms_finance.sql.gz | docker exec -i itilms-postgres-1 psql -U <user> -d itilms_finance`. Because there are twelve independent databases and no cross-database foreign keys, a backup does not have to be perfectly synchronized across all twelve to be useful for disaster recovery, but a report or dashboard restored from out-of-sync backups may show inconsistencies until the event streams catch back up (reporting-service in particular rebuilds its view from Kafka history, not from a point-in-time guarantee).

**Migrations:** each service's `src/main/resources/db/migration/V*__*.sql` runs automatically on that service's next start. Add a new `V2__...sql` rather than editing `V1` once it has run anywhere outside a developer's own machine — Flyway checksums every applied migration and refuses to start against a changed one.

## 9. Rolling out a change

There is no rolling-deploy or blue/green setup in this repository; `docker compose up -d --build <service>` recreates one container in place. For a real deployment:
1. `mvn -DskipTests package` (or better: let CI build and test first — see §10) to produce the jar.
2. Build and push the image for the changed service(s) only — the Dockerfile's build context is the module's own directory, so this does not rebuild anything unrelated.
3. Restart just that service. Because each service owns its data and its Kafka consumer group independently, restarting one does not require restarting its neighbours — the exception is a `config-repo/` change that several services read, which needs `POST /actuator/refresh` on each affected service (or a restart) to pick up.
4. Watch `docker compose logs -f <service>` (or your platform's equivalent) through startup; the health check's `start_period` (60s) is how long a Spring service is given to become ready before a failed check counts against it.

## 10. CI

`.github/workflows/ci.yml` runs on every push and pull request, four jobs:

| Job | What it runs | Catches |
|---|---|---|
| `backend-tests` | `mvn -B -ntp clean test` (every service, common-lib, infrastructure) | Any unit test failure, in any module, in one command — no Testcontainers or live database in CI; every current backend test is a Mockito unit test |
| `gateway-routes` | `node deploy/scripts/check-gateway-routes.mjs` | A controller endpoint the gateway would route to the wrong service, or not at all (see [01-architecture.md](01-architecture.md)) |
| `frontend` | `npm ci && npm run build && npm run lint && npm test` in `frontend/` | A TypeScript or lint error, a build that does not produce a working bundle, or a failing Vitest test (helpers, auth store, menu roles, route guard) |
| `frontend-e2e` | `npx playwright install --with-deps chromium && npx playwright test` in `frontend/`, after `frontend` passes | A broken screen or role gate: sign-in, who sees which menu, the ID card pages. The API is mocked, so no Docker stack is needed. The report is kept for 7 days when it fails ([06-test-plan.md](06-test-plan.md)) |

None of the four jobs builds or pushes a Docker image, and none deploys anywhere — CI here is verification only. Adding an image-build-and-push step (and, eventually, an actual deploy step) is the natural next piece once there is somewhere to deploy to.

`node deploy/scripts/smoke-test.mjs` (the full end-to-end run against a live Docker stack — see [06-test-plan.md](06-test-plan.md)) is **not** in CI: it needs the entire stack running, which GitHub's hosted runners were not set up for here. Run it by hand against a real stack before a release.

---

## Still not done

Recorded here rather than implied by omission:
- **Kubernetes manifests** (Doc D5 defers these deliberately; Compose first).
- **MinIO** has not been exercised end-to-end (§6).
- Backups are a script and a cron line you install yourself (no off-server copy unless you set one up); no rolling/blue-green deploy, no image build in CI (§7–§9 describe how you would do each by hand today).
- **No staging environment exists yet** — only "local" and "whatever you point a fresh `.env` at."

## Database connections and the gateway's concurrency limit (2026-10-31)

- **Postgres runs with `max_connections=200`** (`command:` in compose). Twelve services each hold a pool of up to 10 connections, which with the default of 100 runs out when they are all busy together.
- **Pool size per service** is set with `DB_POOL_MAX` in compose, overridable from `.env`: assessment 20 (a whole class takes a test at once), identity 15, the quiet services (finance, certificate, placement, notification, file, reporting, codeexec) 5, the rest 10 (`<SERVICE>_DB_POOL_MAX`, for example `ASSESSMENT_DB_POOL_MAX`). Keep the sum under `max_connections`.
- **Gateway bulkhead:** each circuit breaker also has a bulkhead, and its default lets only 25 calls to one service run at once; the 26th gets the "temporarily unavailable" answer (503). A class starting a test together went past that (found by the load test, [25-load-testing.md](25-load-testing.md)). `resilience4j.bulkhead` in the gateway's `application.yml` now allows 500 and waits up to 2 seconds.
- **Nginx compresses the bundle.** The site sits behind Caddy, which adds a `Via` header, and Nginx does not compress such requests unless `gzip_proxied any` is set; before 2026-10-31 none of it was compressed (`frontend/deploy/nginx.conf`).

## Updating a running server by hand (when `git pull` is not possible)

If the server has no GitHub access, copy the changed files over with `scp`, then on the server build and replace only what changed:

```
cd ~/ITLMS && mvn -q -DskipTests -pl services/identity-service -am package    # from the repository root, online; add every changed module
cd deploy/docker && docker compose up -d --build --no-deps identity-service     # always --no-deps
```

The Docker image copies a jar that is already built, so changing a source file without running Maven changes nothing in the running service. Recreating a service takes it out for a minute or two (a Java service needs time to be healthy again); a change to Postgres's own settings restarts the database and every service reconnects, so do it out of hours. For the frontend only: `docker compose up -d --build --no-deps frontend`.
