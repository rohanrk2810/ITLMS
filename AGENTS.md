# AGENTS.md

Guidance for AI coding agents (and new developers) working in this repository. Read this first; the detail lives in [docs/](docs/README.md).

## What this is

IT-ILMS: an IT institute's learning and management system. Twelve Spring Boot services plus a stateless code-execution service, one gateway, one PostgreSQL database per service, Kafka between services, LiveKit for live classes, and a React frontend. Built from `IT_Institute_LMS_Complete_Project_Documentation.pdf`, with recorded departures ([docs/02-documentation-review.md](docs/02-documentation-review.md)): microservices instead of a monolith, and live classes as a core feature.

Stack: Java 21, Spring Boot 3.5, Spring Cloud (Gateway, Eureka, Config), PostgreSQL 17, Kafka, Redis, LiveKit, React 19, TypeScript, Tailwind v4, shadcn/ui, Vite.

## Layout

| Path | What |
|---|---|
| `infrastructure/` | `api-gateway` (:8080), `discovery-server` (Eureka), `config-server` |
| `services/` | `identity` 8081, `admission` 8082, `course` 8083, `batch` 8084, `assessment` 8085, `finance` 8086, `certificate` 8087, `placement` 8088, `notification` 8089, `file` 8090, `liveclass` 8091, `reporting` 8092, `codeexec` 8093, and `common-lib` (shared security, exceptions, events) |
| `config-repo/` | Per-service YAML served by the config server |
| `frontend/` | React app (`src/api` one module per backend service, `src/pages`, `src/components`, `src/lib`) |
| `desktop/` | Electron wrapper used for secure tests |
| `deploy/` | Docker Compose (`deploy/docker`), Nginx, Kafka, LiveKit, Postgres, k6 load tests, `scripts/` |
| `docs/` | Architecture, ERD, API, security, design system, PRD, test plan, deployment, feature write-ups |

## Commands

```bash
mvn -B -ntp clean test                                   # all backend unit tests (Mockito, no DB, no Docker)
mvn -q -pl services/<svc> -am clean package              # build one service; keep -am, or a stale common-lib is used
mvn -DskipTests package                                  # build every jar (needed before docker compose --build)
node deploy/scripts/check-gateway-routes.mjs             # every controller path must be routed by the gateway
cd frontend && npm run dev | build | lint                # dev server :5173, tsc + vite build, oxlint
docker compose -f deploy/docker/docker-compose.yml up -d
node deploy/scripts/smoke-test.mjs                       # end-to-end API check against a running stack
```

CI (`.github/workflows/ci.yml`) runs backend tests, the route check, and the frontend build and lint.

## Rules that are easy to break

- **Authorization is real.** Every endpoint has `@PreAuthorize` using the `Roles` constants in `common-lib`, plus ownership checks in the service. Hiding a button is never the control. See [docs/26-security.md](docs/26-security.md).
- **New endpoint = new gateway route** if its prefix is not already routed (`infrastructure/api-gateway/src/main/resources/application.yml`). Run the route checker.
- **One writer per fact.** Services own their tables; others read through Feign or Kafka events, never another service's database. Feign fallbacks return null so a check fails closed.
- **Flyway only.** Schema changes are a new `V<n>__snake_case.sql`; `ddl-auto` is `validate`. Never edit an applied migration.
- **Sidebar roles and route roles are two lists.** `frontend/src/components/nav-items.ts` and the `ProtectedRoute roles=` in `frontend/src/App.tsx` must agree. `ProtectedRoute` is a layout route: wrap routes in it, do not pass children.
- **Two id spaces.** batch-service `trainerId` is admission-service `Trainer.id`, not the identity `User.id`. Create trainers through `POST /api/trainers`, never the bare `POST /api/users`.
- **`docker compose up --build <svc>` rebuilds the whole dependency chain** from whatever is in each module's `target/`. Run `mvn clean package` first, including infrastructure modules, or a stale jar surfaces.
- **Record departures** from the PDF in `docs/02-documentation-review.md`, and update the matching doc when a feature changes.
- **Never commit** `deploy/docker/.env`, secrets, the spec PDF, or Word lock files (`~$*`).
- **Commits:** one short sentence in the style of `git log` (`Area: what changed and why it matters`). No `Co-Authored-By` trailer.

## Working here

- Windows machine, Git Bash and PowerShell. Use forward slashes in Bash. Source files may be CRLF; match the file you edit.
- RAM is tight: do not run Maven and Docker builds at the same time; check free memory (keep over ~2 GB) before a full-stack start.
- Read only the part of a file you need. Prefer the Edit tool over shell rewrites.
- The owner writes in Marathi/Hinglish; reply in Marathi unless asked otherwise.
- A running server is updated by hand (see "Updating a running server" in [docs/07-deployment-guide.md](docs/07-deployment-guide.md)); ask before touching it.

## Conventions

Code style, naming, error handling, testing and commit rules: [docs/29-code-style.md](docs/29-code-style.md). UI tokens and components: [docs/27-design-system.md](docs/27-design-system.md).
