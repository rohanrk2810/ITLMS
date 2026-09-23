# Test Plan

Three layers, each catching a different class of problem, and each run a different way:

| Layer | What it catches | Runs | Needs |
|---|---|---|---|
| [Unit tests](#1-unit-tests) | Business rules and logic bugs, one service at a time | `mvn clean test`, and in CI on every push/PR | Nothing — pure Mockito, no database, no Docker |
| [Gateway route check](#2-gateway-route-check) | A controller endpoint the gateway sends to the wrong service, or nowhere | `node deploy/scripts/check-gateway-routes.mjs`, and in CI | Nothing — reads source files and the gateway's own config |
| [Smoke test](#3-end-to-end-smoke-test) | Wiring between services: Kafka events, Feign calls, LiveKit, real HTTP through the gateway | `node deploy/scripts/smoke-test.mjs`, by hand | A running Docker stack (**not** in CI — see [07-deployment-guide.md §10](07-deployment-guide.md#10-ci)) |

None of the three currently measures or gates on code coverage (Jacoco is wired into every module's `mvn verify` and produces a report under `target/site/jacoco/`, but nothing reads it as a pass/fail gate — see "Gaps" below).

---

## 1. Unit tests

Every service (and `common-lib`) has its own Mockito-based unit test suite under `src/test/java`, run by `mvn clean test` — no Spring context, no database, no Testcontainers anywhere in the project. This is why `backend-tests` in CI is a single `mvn -B -ntp clean test` across the whole reactor and finishes in a few minutes rather than needing infrastructure to spin up.

Counts as of this writing (`mvn -B -ntp clean test`, full reactor, 2026-09-23 — **314 tests, 0 failures, BUILD SUCCESS**):

| Service | Tests | Notable coverage |
|---|---|---|
| identity | 35 | login, refresh rotation + reuse-revokes-session-family, password reset (issue/consume/single-use/rate-limit), self-registration on/off, lockout after 5 failed attempts, logout / logout-all |
| admission | 37 | lead → follow-up → convert, student admission, status changes, `UserEventConsumer` (profile creation from `user-created`) |
| course | 50 | curriculum CRUD + reordering, publish validation, who can open the curriculum (`EnrollmentStatus.allowsContentAccess()`), progress recording, keeping in step with other services (event consumers) |
| batch | 26 | marking the register, attendance-derived-from-a-live-class, attendance percentage, correction requires a reason |
| assessment | 23 | scoring rules, marking one question, percentage/pass mark, accepting submissions, **`AnswerKeyExposureTest`** (the served attempt payload never carries `correct`) |
| finance | 21 | splitting a fee into instalments, applying payments oldest-first, outstanding-amount formula (Doc S14), reminder/schedule rules, raising a fee plan |
| certificate | 12 | eligibility rules, verification + PDF |
| placement | 10 | who may apply, editing a job once students have applied, moving through the pipeline (Doc S7.4) |
| notification | 22 | announcement rules, email composition, retrying failed emails, emails carrying a password/reset link (and never storing them), delivery rules |
| file | 40 | `FileAccessRules` (category × role matrix, incl. `RESUME`), upload validation, `listMine` |
| liveclass | 21 | percentage and verdict, an unattended class taken to have ended, a student's time in the room (reconnects summed), class length used as the denominator, LiveKit webhook handling |
| reporting | 17 | attendance percentage, tabular export (CSV/Excel/PDF), dashboard service, report export service |
| **Total** | **314** | |

`common-lib`, `config-server`, `discovery-server` and `api-gateway` have no test sources — there is no business logic in them to unit test (config-server and discovery-server are Spring Cloud boilerplate; api-gateway's behaviour is covered by the gateway route check and, end-to-end, by the smoke test).

Two real defects were found and fixed by writing these tests (identity, batch, admission and course were the last four services to get a suite — see [documentation review](02-documentation-review.md) for the pattern of tracking defects found during review):
- **identity:** `login()`/`refresh()` were rolled back by Spring on the exceptions they use for control flow (`BadCredentialsException`, `ForbiddenOperationException`), so the failed-attempt counter, the lockout, and refresh-token-reuse revocation were silently never persisted. Fixed with `@Transactional(noRollbackFor = {...})` on both methods (`AuthServiceImpl.java`).
- **course:** `getCurriculum` unlocked lesson content for a DROPPED or SUSPENDED enrolment, and returned 404 for an enrolled student on an archived course. Fixed by adding `EnrollmentStatus.allowsContentAccess()` (true for ACTIVE and COMPLETED only) and using it in the one place that decides content access.

Both are exactly the kind of bug this layer exists to catch: neither shows up as a compile error, a wrong HTTP status on the happy path, or a gateway routing problem — only as "the counter never goes up" or "an edge-case status is treated wrong," which is what a unit test asserting on repository interactions or on status transitions is for.

**Running it:**
```
mvn clean test                                    # whole reactor
mvn -pl services/identity-service -am test         # one service (with its dependencies)
```
Per-module results land in `services/<name>/target/surefire-reports/`.

## 2. Gateway route check

`deploy/scripts/check-gateway-routes.mjs` reads every `@GetMapping`/`@PostMapping`/etc. across every controller in `services/`, reads the route list in `infrastructure/api-gateway/src/main/resources/application.yml`, and simulates Spring Cloud Gateway's own first-match `Path=` predicate matching for each endpoint. It fails (non-zero exit) if any endpoint would be routed to a service other than the one that implements it, or to no route at all.

This exists because a broad pattern declared early (`/api/students/**`) can silently swallow a path another service owns, and the failure mode at runtime is a confusing 404 or 500 from the *wrong* service — exactly the kind of mistake that is invisible to a unit test (which never goes near the gateway) and only shows up in the smoke test as an unexplained failure several steps later. Running it standalone turns that into an immediate, specific error.

Current result: **181 public endpoints checked, 0 misrouted**. `/internal/**` paths are excluded by design (the gateway refuses them outright — see [04-api-reference.md](04-api-reference.md#internal-endpoints)).

```
node deploy/scripts/check-gateway-routes.mjs
```

Runs in CI on every push/PR as the `gateway-routes` job, needing only Node — no build, no database.

## 3. End-to-end smoke test

`deploy/scripts/smoke-test.mjs` runs a single continuous story through a **live Docker stack**, entirely through the gateway (`http://localhost:8080`), the way a browser would: sign in as the bootstrap admin, publish a course, admit a trainer and students (through admission-service's Feign call into identity-service), open an online batch, schedule a live class, join it as both student and trainer, replay LiveKit's signed webhooks, and check the attendance register that comes out the other side. From there it carries the same students through a quiz, an assignment, fees and a payment, a certificate, a file upload, a job application, and finally reporting-service's dashboard and audit log.

This is the only layer that exercises what the other two structurally cannot:
- **Kafka events actually consumed** across service boundaries (`user-created` → admission creates a profile; `student-admitted` → finance raises a fee plan; `session-scheduled` → liveclass provisions a room; `live.attendance-computed` → batch writes the register; ...).
- **Feign calls that actually reach a live peer**, not a mock.
- **LiveKit's real webhook signature verification**, and the room lifecycle rules (a room closing early vs. closing near the scheduled end).
- **Temporary passwords and reset tokens actually arriving by email** — read back from Mailpit's API rather than asserted never to have been sent.
- **Security boundaries under real HTTP**: no token → 401, wrong student's data → 403, an unsigned LiveKit webhook → 401, a self-registration attempt when the setting is off → 403, an internal endpoint reached from outside → 404.

It is intentionally the slowest and most expensive layer to run and the only one that writes real rows into real databases — **never point it at an institute's actual stack**, only at a disposable development one.

**What it does not replace:** it is one linear story, not a matrix of every role × every edge case — that breadth is what the unit test layer is for. A gap in the smoke test's story is a gap in *integration* coverage, not necessarily a gap in *logic* coverage.

**Current size:** 119 `check(...)` assertion call sites in the script (one is the helper's own definition, so effectively ~118 checks across the whole run) — up from 116 in an earlier commit (`d8a6f25`), reflecting the file/assignment/notification/placement/reporting sections added since. This count is from reading the script, **not from a fresh run** — the Docker stack was stopped after the last live session and was not started for this documentation pass (see [05-local-setup.md](05-local-setup.md) — start it only when actually exercising the system).

**Running it:**
```
mvn -DskipTests package                              # build every jar first
cd deploy/docker && docker compose up -d --build      # wait for every row to read "healthy"
node deploy/scripts/smoke-test.mjs                    # from the repository root, or pass the docker dir explicitly
```
Exits non-zero if any check failed; prints `PASS`/`FAIL` per check plus a final `<passed> passed, <failed> failed`.

---

## Gaps

Recorded here rather than left implicit:

- **Not run against a live stack in this documentation pass.** The 314 unit tests and the 181-endpoint route check were both re-run fresh (2026-09-23) to write this document; the smoke test was not, because that needs Docker running and this task was documentation-only. Re-run it before trusting anything about live wiring.
- **No coverage gate.** Jacoco reports exist per module but nothing fails a build for low coverage.
- **No Testcontainers / real-database tests.** Every backend test is a Mockito unit test with mocked repositories; nothing exercises an actual Flyway migration against a real Postgres except the smoke test indirectly doing so through the running stack.
- **Smoke test areas still not exercised, per the last live run:** a submission with file attachments (the third of three `@OneToMany` nullable-FK fixes from 222f277), the notification inbox and announcements UI path, MinIO as the storage backend, assignment late/rework paths, and the frontend in a real browser.
- **No frontend test suite** (no Vitest/RTL installed yet — see the frontend build notes in project memory).
- **Smoke test is not in CI** (needs a full Docker stack — see [07-deployment-guide.md §10](07-deployment-guide.md#10-ci)); it is the one layer that depends on a human (or a future CI runner with more resources) remembering to run it.
