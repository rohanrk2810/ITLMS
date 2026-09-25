# IT-ILMS

**IT Institute Learning & Management System** — one platform for the whole student journey: enquiry, admission, batch, fees, attendance, learning, assessment, certificate, placement, and live classes.

Built from `IT_Institute_LMS_Complete_Project_Documentation.pdf`, with two deliberate departures from that document: a **microservices architecture** instead of a monolith, and **live classes** (LiveKit) as a core feature instead of a future enhancement. Every departure is recorded in [docs/02-documentation-review.md](docs/02-documentation-review.md).

**Stack:** Java 21 · Spring Boot 3.5 · Spring Cloud Gateway/Eureka/Config · PostgreSQL 17 · Kafka · Redis · LiveKit · React 19 + TypeScript + Tailwind v4 + shadcn/ui

---

## What it does

| Area | Covers |
|---|---|
| **Admissions** | Leads, follow-ups, converting a lead to a student with a course, batch and fee plan in one step |
| **Academics** | Course catalog, modules and lessons, batches, timetables, attendance (manual and automatic) |
| **Live classes** | LiveKit-backed video rooms, role-scoped join tokens, automatic attendance from time spent in the room |
| **Assessment** | Assignments with file submissions, MCQ tests with server-side scoring, results |
| **Finance** | Fee plans, instalment schedules, payments, receipts, overdue reminders |
| **Certificates** | A configurable completion rule checked live against every owning service, PDF issue, public verification by code |
| **Placement** | Companies, job openings with enforced eligibility, applications, interview pipeline |
| **Notifications** | In-app notifications, announcements (by role/batch/course), email via an outbox (never loses a message if mail is briefly down) |
| **Files** | Uploads with type/size rules per category, access control, controlled download |
| **Reporting** | Institute dashboard, exports (CSV/Excel/PDF), and a cross-service audit log |

## Architecture, in brief

Twelve independently-deployable Spring Boot services (plus a thirteenth, stateless one that runs practice-editor code in a sandbox: [docs/08-code-execution.md](docs/08-code-execution.md)), one gateway, one database per service, facts exchanged over Kafka rather than shared tables:

```
        browser (React)
             │
             ▼
      api-gateway :8080   ← validates the JWT once, rate-limits, refuses /internal/** from outside
             │  lb://service-name (Eureka)
     ┌───────┼──────────────────────────────┐
     ▼       ▼                              ▼
 identity  admission  course  batch  liveclass  assessment  finance
 certificate  placement  notification  file  reporting
     │                              │
own PostgreSQL database        LiveKit media server (video/audio never touches IT-ILMS)
     │
     ▼
   Kafka — events between services, e.g. student-admitted → a fee plan is raised automatically
```

Full write-up — the call graph, every event, what the design costs, and a request traced end to end: **[docs/01-architecture.md](docs/01-architecture.md)**.

---

## Start here

Already set up on this machine before (jars built, `deploy/docker/.env` exists)?

```bash
docker compose -f deploy/docker/docker-compose.yml up -d
docker compose -f deploy/docker/docker-compose.yml ps      # wait until every row reads "healthy"
```

Open **http://localhost:5173** and sign in with the administrator from `deploy/docker/.env` (`BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`). Made code changes since the last build? Rebuild first: `mvn -DskipTests package`, then add `--build` to `up`.

### Setting this up on a machine that has never run it before

⚠️ This repository has not been pushed to any remote yet — get the code onto the new machine first (copy the folder, a drive, or push it somewhere and clone from there).

1. **Install:** JDK 21+, Maven 3.9+, Docker Desktop (give it **at least 6 GB** memory in Settings → Resources), Node.js 20+.
2. **Build the jars** (from the repository root — builds all 17 Maven modules):
   ```bash
   mvn -DskipTests package
   ```
3. **Create the environment file** and fill in every `CHANGE_ME`:
   ```bash
   cd deploy/docker
   cp .env.example .env
   ```
   `JWT_SECRET`, `POSTGRES_PASSWORD`, `LIVEKIT_API_SECRET` need long random values (e.g. `openssl rand -base64 48`); `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD` become the first administrator. `.env` is git-ignored and never committed — no service starts without its own secret; there is no fallback default.
4. **Start everything** (the first run also downloads base images, so it's slower than every run after):
   ```bash
   docker compose up -d --build
   docker compose ps       # wait for every row to read "healthy"
   ```
5. **Sign in** at http://localhost:5173 with the admin credentials from step 3. This is a brand-new, empty database — none of another machine's courses, batches or students carry over automatically.

Full instructions — running a single service from an IDE, live classes over a LAN, and what to do when something breaks — are in **[docs/05-local-setup.md](docs/05-local-setup.md)**. Taking this beyond one developer's machine (reverse proxy/HTTPS, real email, MinIO, resource sizing, backups): **[docs/07-deployment-guide.md](docs/07-deployment-guide.md)**.

### What runs where

| Address | What |
|---|---|
| http://localhost:5173 | The frontend |
| http://localhost:8080 | API gateway — every API request goes here |
| http://localhost:8080/swagger-ui.html | Every endpoint, live, from one page |
| http://localhost:8761 | Eureka — which services have registered |
| http://localhost:8025 | Mailpit — every email the system sends, caught locally (dev only) |

Every business service's own port (8081–8091) is deliberately **not** published — everything reaches them through the gateway, exactly as it would in production.

---

## Documentation

| Document | What it covers |
|---|---|
| [docs/01-architecture.md](docs/01-architecture.md) | How the services fit together, and what the design costs |
| [docs/02-documentation-review.md](docs/02-documentation-review.md) | Every departure from the source document, and why |
| [docs/03-erd.md](docs/03-erd.md) | Every service's schema, generated from its Flyway migrations |
| [docs/04-api-reference.md](docs/04-api-reference.md) | Every endpoint and who may call it, generated from the actual `@PreAuthorize` annotations |
| [docs/05-local-setup.md](docs/05-local-setup.md) | Running it day to day, and troubleshooting it |
| [docs/06-test-plan.md](docs/06-test-plan.md) | What's tested, and how |
| [docs/07-deployment-guide.md](docs/07-deployment-guide.md) | What a real deployment adds on top of local Docker Compose |
| http://localhost:8080/swagger-ui.html | Every endpoint, live, from one page (once the stack is up) |

## Project layout

```
services/          one folder per business service, plus common-lib (shared security, events, error handling)
infrastructure/    api-gateway, discovery-server, config-server
config-repo/       configuration the config server serves to every service
frontend/          React + TypeScript + Tailwind + shadcn/ui
deploy/docker/     the local Docker Compose stack, Dockerfile, .env.example
deploy/scripts/    checks you can run against a running system (route checker, smoke test)
docs/              architecture, ERD, API reference, test plan, deployment guide
```

## Status

All 12 business services, the platform (gateway, discovery, config server) and the frontend are built and have run together in Docker behind the gateway — a 116-check end-to-end smoke test passes against the full stack, including the frontend.

Nothing has been pushed to a remote yet; the work sits on stacked feature branches, all fast-forwarded onto `master` locally.

Still open: Kubernetes manifests (Compose first, by design — Doc departure D5), MinIO storage wired in code but not yet exercised end-to-end, no CI image-build/deploy step, no staging environment. Full list: [docs/07-deployment-guide.md § Still not done](docs/07-deployment-guide.md#still-not-done).

## Checks

```bash
mvn test                                        # backend unit tests, every module
node deploy/scripts/check-gateway-routes.mjs    # every endpoint reaches its own service, none is misrouted
node deploy/scripts/smoke-test.mjs              # a full run through a live stack, including security boundaries
cd frontend && npm run build && npm run lint    # frontend build and lint
```

## Security notes

- The gateway is the only way in from outside; it validates every JWT and strips any identity header a caller tried to inject. Each service still validates the token itself rather than trusting the network it's on.
- `/internal/**` paths (service-to-service only) are refused with a 404 at the gateway before authentication is even checked — they are unreachable from outside regardless of any token.
- Secrets (`JWT_SECRET`, database passwords, LiveKit's API secret) come only from environment variables with no default; a service refuses to start rather than run with a placeholder.
- A temporary password or a password-reset token is only ever emailed — never written to a database column or a log line.

Known limitations, tracked deliberately rather than by omission: [docs/02-documentation-review.md](docs/02-documentation-review.md) (see the "Open items" and "Limitations" sections).
