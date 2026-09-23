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

Locally, `mailpit` catches every email so nothing is ever really sent (`MAIL_ENABLED=true` pointed at `mailpit:1025`). In any other environment:
- Point `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD` at a real SMTP relay (a transactional email provider is the usual choice) and set `MAIL_SMTP_AUTH=true`, `MAIL_SMTP_TLS=true`.
- **Do not deploy `mailpit` anywhere but a developer's own machine or CI.** It is an open, unauthenticated mail catcher with no purpose once real email is configured.
- notification-service is the only consumer of these settings. Temporary passwords and password-reset tokens go out through it and are never written to any database table or log line (see [02-documentation-review.md §7](02-documentation-review.md#7-defects-found-and-fixed-during-this-review), the fifth defect) — this holds regardless of which SMTP provider is behind `MAIL_HOST`.

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
- **frontend: 128M** — static Nginx only.

Platform services (gateway, config-server, discovery-server) are 512M each. The whole stack is roughly **4.5GB** and 18 containers; Docker Desktop needs at least 6GB allocated, or containers get OOM-killed rather than failing with a clear error. `JAVA_OPTS`'s `-XX:MaxRAMPercentage=65` (70 in the shared Dockerfile default) means the JVM heap is sized from *the container's* limit — raising a service's memory limit without also raising `JAVA_OPTS` if you override it wastes the extra memory outside the heap.

Kafka runs as `apache/kafka-native` (GraalVM native build) specifically for fast startup and low idle memory in development; production deployments should use the JVM image (`apache/kafka`) with the same broker settings, which is more battle-tested at real throughput.

## 8. Database

One PostgreSQL instance hosts all twelve databases locally (`postgres:17-alpine`, one container, `postgres/init/` creates each database on first start). Each service still only ever connects to and migrates its own database (Flyway, `ddl-auto: validate` — the JPA layer never alters schema at runtime). Splitting the databases across separate Postgres instances, or moving to a managed Postgres service, requires no code change: only `DB_HOST`/`DB_PORT`/credentials per service.

**Backup:** nothing in this repository automates a backup — that is an operational choice for wherever Postgres actually runs (a managed service's own snapshotting, or `pg_dump` per database on a schedule). Because there are twelve independent databases and no cross-database foreign keys, a backup does not have to be perfectly synchronized across all twelve to be useful for disaster recovery, but a report or dashboard restored from out-of-sync backups may show inconsistencies until the event streams catch back up (reporting-service in particular rebuilds its view from Kafka history, not from a point-in-time guarantee).

**Migrations:** each service's `src/main/resources/db/migration/V*__*.sql` runs automatically on that service's next start. Add a new `V2__...sql` rather than editing `V1` once it has run anywhere outside a developer's own machine — Flyway checksums every applied migration and refuses to start against a changed one.

## 9. Rolling out a change

There is no rolling-deploy or blue/green setup in this repository; `docker compose up -d --build <service>` recreates one container in place. For a real deployment:
1. `mvn -DskipTests package` (or better: let CI build and test first — see §10) to produce the jar.
2. Build and push the image for the changed service(s) only — the Dockerfile's build context is the module's own directory, so this does not rebuild anything unrelated.
3. Restart just that service. Because each service owns its data and its Kafka consumer group independently, restarting one does not require restarting its neighbours — the exception is a `config-repo/` change that several services read, which needs `POST /actuator/refresh` on each affected service (or a restart) to pick up.
4. Watch `docker compose logs -f <service>` (or your platform's equivalent) through startup; the health check's `start_period` (60s) is how long a Spring service is given to become ready before a failed check counts against it.

## 10. CI

`.github/workflows/ci.yml` runs on every push and pull request, three independent jobs:

| Job | What it runs | Catches |
|---|---|---|
| `backend-tests` | `mvn -B -ntp clean test` (every service, common-lib, infrastructure) | Any unit test failure, in any module, in one command — no Testcontainers or live database in CI; every current backend test is a Mockito unit test |
| `gateway-routes` | `node deploy/scripts/check-gateway-routes.mjs` | A controller endpoint the gateway would route to the wrong service, or not at all (see [01-architecture.md](01-architecture.md)) |
| `frontend` | `npm ci && npm run build && npm run lint` in `frontend/` | A TypeScript or lint error, or a build that does not produce a working bundle |

None of the three jobs builds or pushes a Docker image, and none deploys anywhere — CI here is verification only. Adding an image-build-and-push step (and, eventually, an actual deploy step) is the natural next piece once there is somewhere to deploy to.

`node deploy/scripts/smoke-test.mjs` (the full end-to-end run against a live Docker stack — see [06-test-plan.md](06-test-plan.md)) is **not** in CI: it needs the entire stack running, which GitHub's hosted runners were not set up for here. Run it by hand against a real stack before a release.

---

## Still not done

Recorded here rather than implied by omission:
- **Kubernetes manifests** (Doc D5 defers these deliberately; Compose first).
- **MinIO** has not been exercised end-to-end (§6).
- **No automated backup**, no rolling/blue-green deploy, no image build in CI (§7–§9 describe how you would do each by hand today).
- **No staging environment exists yet** — only "local" and "whatever you point a fresh `.env` at."
