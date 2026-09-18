# IT-ILMS

**IT Institute Learning & Management System** — one platform for the whole student journey: enquiry, admission, batch, fees, attendance, learning, assessment, certificate, placement. Built from `IT_Institute_LMS_Complete_Project_Documentation.pdf`.

Java 21 · Spring Boot 3.5 · Spring Cloud · PostgreSQL · Kafka · LiveKit · React (to come)

---

## Start here

```bash
mvn -DskipTests package
cd deploy/docker && cp .env.example .env     # then fill in the secrets
docker compose up -d --build
```

Then open http://localhost:8080/swagger-ui.html and sign in as the administrator from your `.env`.

Full instructions, including how to run a single service from an IDE and what to do when something breaks: **[docs/05-local-setup.md](docs/05-local-setup.md)**.

## Documentation

| Document | What it covers |
|---|---|
| [docs/01-architecture.md](docs/01-architecture.md) | How the services fit together, and what the design costs |
| [docs/02-documentation-review.md](docs/02-documentation-review.md) | Every departure from the source document, and why |
| [docs/05-local-setup.md](docs/05-local-setup.md) | Running it, troubleshooting it |
| http://localhost:8080/swagger-ui.html | Every endpoint, live, from one page |

## Layout

```
services/          one folder per service, plus common-lib
infrastructure/    api-gateway, discovery-server, config-server
config-repo/       configuration the config server serves
deploy/docker/     the local stack
deploy/scripts/    checks you can run against a running system
docs/
```

## Status

Built and verified end to end in Docker: **identity, admission, course, batch, liveclass**, behind the gateway, with discovery, configuration and events.

Built and unit-tested, not yet run in the full stack: **assessment** (assignments, MCQ tests with server-side scoring, results) **finance** (fee plans, payments, receipts, overdue reminders) **certificate** (completion rule, PDF, public verification) **placement** (companies, jobs with enforced eligibility, interview pipeline) and **notification** (in-app notifications, announcements, email through an outbox).

Still to build: file, reporting — and the React frontend.

## Checks

```bash
mvn test                                        # unit tests
node deploy/scripts/check-gateway-routes.mjs    # every endpoint reaches its own service
node deploy/scripts/smoke-test.mjs              # a full run through a live stack
```
