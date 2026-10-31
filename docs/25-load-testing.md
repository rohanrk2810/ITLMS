# Load testing

Scripts for [k6](https://k6.io) in `deploy/loadtest/`. They answer one question: what happens when a whole class signs in and takes a test at the same moment.

## Running them

Local stack only. `setup.mjs` refuses any address other than `localhost`, because it writes real rows (a course, a trainer, N students, a batch and a published quiz).

1. Install k6 (`winget install GrafanaLabs.k6`).
2. Start the stack with the human check off for this run (it does not change `.env`): `CAPTCHA_ENABLED=false docker compose up -d --build` in `deploy/docker`. On a laptop with about 8 GB for Docker, start only what the tests use: postgres, redis, kafka, config-server, discovery-server, api-gateway, identity-, admission-, course-, batch- and assessment-service.
3. `node deploy/loadtest/setup.mjs 150` makes the data and writes `loadtest-data.json` (git-ignored). Every student gets one known password, stored as a bcrypt hash of the same cost (12) as production, so a sign-in costs what it costs for real.
4. Run a script:

| Script | What it does |
|---|---|
| `k6 run -e VUS=150 deploy/loadtest/login.js` | Students sign in and open `/me`, the branding and the available quizzes, in a loop. Each student has an address of their own, as at home. `-e ONE_ADDRESS=1` puts them all behind one address, as on a college network. |
| `k6 run -e VUS=150 deploy/loadtest/quiz.js` | Everyone starts the same quiz at once, saves 20 answers and submits. Sign-in is done beforehand, paced (it would test the sign-in limit, not the quiz). |

A student can start a quiz twice (`attemptsAllowed: 2`); run `setup.mjs` again for fresh data before the third run. Do not leave `log_statement=all` switched on in Postgres while measuring: logging every statement slowed one run to a crawl.

## What it found (2026-10-31, local)

| Finding | Cause | Fix |
|---|---|---|
| With 50 people at once, 21 got "temporarily unavailable" (503); with 100, 96 did. Direct to the service, 80 at once were fine. | The gateway's circuit breaker carries a bulkhead, and Resilience4j's default allows only 25 concurrent calls per service. | `resilience4j.bulkhead` in the gateway: 500 calls, wait 2 s. |
| Admitting ten students together gave a 500 for one of them. | The student code (`STU-2026-000096`) comes from `COUNT + 1`; two admissions chose the same code, and the retry could not work because the session that hit the unique constraint was no longer usable. | `pg_advisory_xact_lock` before choosing the code (`StudentRepository.lockCodeSequence`). |
| Each answer save ran about 26 queries: one extra select per answer for its options, one per question for its test cases, and a rewrite of every answer although nothing had changed. | Lazy collections loaded one at a time; the page sends all answers on each autosave. | `@BatchSize(50)` on both collections; an unchanged answer is skipped. |
| On one shared address, 92% of sign-ins and 71% of `/me` calls were refused (429). | `/api/auth/**` had one limit, 5 per second, for sign-in and for the calls a page makes after it. | Two routes: sign-in 15 per second (burst 40), `/me`, `/refresh` and the like 50 per second (burst 150). |

## Results

150 students starting one quiz together (20 questions each; 3450 requests, no failures). Local PC, Docker with 7.6 GB.

| | Before the fixes | After |
|---|---|---|
| Answer save, p95 | 604 ms | 89 ms |
| Submit, p95 | 465 ms | 73 ms |
| Start the quiz, p95 | 1.68 s | 1.07 s |
| Database queries for 20 students' whole quiz | 11 840 | 5 436 |

150 students signing in over and over (each loop: sign in, `/me`, branding, quizzes), one address each: sign-in p95 3.6 s before, 1.1 s after, no failures. All behind one address: 8% of sign-ins accepted before, 77% after (the rest are because every one of the 150 signs in again every 5-10 seconds, which people do not).

## Limits to keep in mind

- These are numbers from a laptop with the load generator on the same machine; the server (4 vCPU, 16 GB) has not been measured. Do that out of hours before an important exam.
- Signing in is CPU-bound (bcrypt, cost 12): expect roughly 10-15 sign-ins per second on 4 cores. Ask a large class to sign in a few minutes before a test opens instead of in the same second.
- Not tested yet: joining a live class (LiveKit), running code (Piston), more than 150 at once.
