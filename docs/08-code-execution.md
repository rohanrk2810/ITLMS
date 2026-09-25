# Code execution (practice editor)

A lesson can carry a code editor. The student writes code under the lesson, presses **Run**, and sees the output. Nothing runs in the browser and nothing runs inside IT-ILMS: the code goes to **codeexec-service**, which decides whether the run may happen and hands it to a sandbox that you host yourself: **Judge0** or **Piston**.

```
browser ──/api/code/run──▶ gateway ──▶ codeexec-service ──▶ Judge0 or Piston (yours)
                                        signed in? language on?      compiles + runs with
                                        size ok? too often? busy?    time / memory / no network
```

## Which sandbox

`CODEEXEC_ENGINE` in `deploy/docker/.env` picks one (`judge0` is the default). Everything else - the endpoints, the editor, the limits - is the same.

| | Judge0 | Piston |
|---|---|---|
| Host needs | Linux with **cgroup v1** (not Docker Desktop) | **cgroup v2, v1 off** (Docker Desktop is fine) |
| Java | 17 / 21 (ids to be checked against your instance) | **15 only**: no records or sealed classes; runs the first class in the file |
| Other languages | Python 3.11, C/C++ GCC 14, C# Mono, SQLite (ids from memory, unchecked) | Python 3.12, C/C++ GCC 10, C# Mono 6.12, SQLite 3.36 (all run and checked) |
| In this repository | not run against a real instance | run against a real instance: [Piston](#piston-on-docker-desktop) |
| Extra containers | Postgres, Redis, server, workers | one |

## What is in the repository

| Piece | Where |
|---|---|
| Lesson fields `codeLanguage`, `starterCode` | course-service (`V2__lesson_practice_editor.sql`) |
| The shared list of languages | `com.itilms.common.code.CodeLanguage` in common-lib |
| The service (port 8093, stateless: no database, no Kafka) | `services/codeexec-service`, `config-repo/codeexec-service.yml` |
| Editor and Run button | `frontend/src/components/practice-editor.tsx` |
| Choosing a language when adding a lesson | `frontend/src/pages/courses/course-editor-page.tsx` |

**Languages:** Java, Python, C, C++, C#, SQL (SQLite). **PL/SQL is not supported** — it needs an Oracle database behind it, which a sandbox does not provide.

## Endpoints

| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/code/languages | any signed-in user | Languages that are switched on |
| POST | /api/code/run | any signed-in user | Run `{language, sourceCode, stdin?}` |
| GET | /api/code/status | ADMIN | Is the sandbox configured and reachable, and does it know every language we mapped? |

A compile error or a crash in the student's code is a normal `200` whose `outcome` says `COMPILE_ERROR`, `RUNTIME_ERROR` or `TIME_LIMIT_EXCEEDED`. Errors: `422` unknown or disabled language, code too long; `429` too many runs (10 a minute, 300 a day, per user) or the runner is busy (8 at once); `503` Judge0 not set up or not reachable.

## Piston (on Docker Desktop)

Piston is an optional container in `deploy/docker/docker-compose.yml` (profile `piston`, so a plain `up` does not start it). It needs about 1.5 GB of RAM while running code; set `PISTON_MEMORY_LIMIT` and `PISTON_MAX_CONCURRENT_JOBS` in `.env` to change that.

1. Start it: `docker compose --profile piston up -d piston`
2. Install the languages, once (they go into the `piston-packages` volume, a few minutes in total):
   ```bash
   docker compose --profile piston exec -T piston node - install java@15.0.2 python@3.12.0 gcc@10.2.0 mono@6.12.0 sqlite3@3.36.0 < deploy/scripts/piston-packages.js
   ```
   `... node - list < deploy/scripts/piston-packages.js` shows what exists and what is installed.
3. In `.env`: `CODEEXEC_ENGINE=piston` and `PISTON_URL=http://piston:2000`, then `docker compose up -d codeexec-service`.
4. As ADMIN, `GET /api/code/status` should list every language as available.

**If step 2 fails with `ECONNREFUSED ... 185.199.109.133:443`** (Piston crashes and restarts): on the network this was set up on, that one of GitHub's four content addresses is unreachable, and Piston's Node picks the first one it is given. Run the install in a throwaway container that names a working address for it, then start the normal one on the same volume:

```bash
docker stop itilms-piston-1
docker run -d --name piston-install --privileged --memory 1536m \
  --add-host release-assets.githubusercontent.com:185.199.110.133 \
  --add-host objects.githubusercontent.com:185.199.110.133 \
  -v itilms_piston-packages:/piston/packages ghcr.io/engineer-man/piston
docker exec -i piston-install node - install java@15.0.2 ... < deploy/scripts/piston-packages.js
docker rm -f piston-install && docker compose --profile piston up -d piston
```

**What was measured on a running Piston** (Docker Desktop, cgroup v2), and what the runner does about it:

- A compile error in **C, C++ and C#** comes back from a separate compile stage. In **Java** there is none - Piston runs `java Main.java`, so the compiler's messages arrive in the run stage; the runner recognises the JDK's `error: compilation failed` and reports it as a compile error.
- **Java runs the first class in the file.** A helper class placed before `Main` gives `can't find main(String[]) method in class: Helper`; the runner adds a tip, but the reliable fix is to write `Main` first (starter code should).
- **File names are given without an extension** (`Main`, `main`): Piston adds `.java`/`.c`/`.cpp`/`.cs` itself, and `Main.java` would show as `Main.java.java` in every error.
- **A JVM burns CPU on several threads**: Hello World used 3-5 s of CPU in 2-3 s of wall time, so the CPU limit is set equal to the wall limit (15 s) instead of `cpu-time-seconds`.
- **Limits above Piston's ceilings are refused** (`400 run_timeout cannot exceed the configured limit`), so the ceilings in `docker-compose.yml` must stay at or above `limits` in `config-repo/codeexec-service.yml`.
- **Output over the ceiling** (`PISTON_OUTPUT_MAX_SIZE`, 64 KiB) kills the program with status `OL`; the student is told "Output Limit Exceeded" instead of Piston's own wording. **Memory over the limit** (256 MB) kills it with exit code 137, reported as "killed, probably out of memory".
- **No network:** a program that opens a socket fails.
- **Speed:** Python answers in ~0.2 s, C and SQL under 1 s, C# and C++ 1.5-2 s, Java 1.5-3.5 s.

## Setting up Judge0

**Judge0 must run on a Linux host that uses cgroup v1.** Its sandbox (isolate) does not work under cgroup v2. Judge0's own changelog (v1.13.1, the latest release at the time of writing) says to add `systemd.unified_cgroup_hierarchy=0` to `GRUB_CMDLINE_LINUX` and recommends Ubuntu 22.04.

This means **Docker Desktop on Windows/macOS will not do**: it runs a cgroup v2 Linux VM. Use a Linux server or VM, separate from this stack.

On that server (Ubuntu 22.04, Docker and Docker Compose installed) — summary of Judge0's own deployment procedure, check it against the CHANGELOG of the release you download:

1. In `/etc/default/grub`, add `systemd.unified_cgroup_hierarchy=0` to `GRUB_CMDLINE_LINUX`, run `sudo update-grub`, reboot.
2. Download and unpack the release:
   ```bash
   wget https://github.com/judge0/judge0/releases/download/v1.13.1/judge0-v1.13.1.zip
   unzip judge0-v1.13.1.zip && cd judge0-v1.13.1
   ```
3. In `judge0.conf` set `REDIS_PASSWORD` and `POSTGRES_PASSWORD`. Also set `AUTHN_HEADER=X-Auth-Token` and an `AUTHN_TOKEN` (a long random string): without them anyone who can reach the port can run code on it.
4. Start it:
   ```bash
   docker compose up -d db redis
   sleep 10
   docker compose up -d
   ```
5. Judge0 listens on port 2358. **Do not expose it to the internet.** Allow only the machine that runs codeexec-service (firewall or private network).

## Connecting IT-ILMS to it

In `deploy/docker/.env`:

```
JUDGE0_URL=http://<judge0-server>:2358
JUDGE0_AUTH_HEADER=X-Auth-Token
JUDGE0_AUTH_TOKEN=<the AUTHN_TOKEN from judge0.conf>
```

Then `mvn -DskipTests package` and `docker compose up -d --build codeexec-service`. Blank `JUDGE0_URL` is a valid state: lessons still work and the Run button says code running is not set up yet.

**Then check it.** Sign in as ADMIN and call `GET /api/code/status` (Swagger: `http://localhost:8080/swagger-ui.html`, group "13. Code Exec"). It compares each language id in `config-repo/codeexec-service.yml` with what your Judge0 actually has and names any that do not exist. The ids in that file are Judge0 CE 1.13.1's from memory, not confirmed against a running instance; `GET http://<judge0>:2358/languages` lists the real ones. Change `language-ids` and restart, or remove a language's line to switch it off.

## Limits worth knowing

- **Per-user limits live in this service's memory**, so with more than one codeexec instance the limit is per instance. Fine for one institute.
- **No queue.** When 8 runs are already in flight, the next student is told the runner is busy instead of waiting. Raise `CODEEXEC_MAX_CONCURRENT` if the Judge0 host has spare CPUs.
- **Any signed-in user can run code**, enrolled or not; the service does not check the lesson. It is a sandbox with rate limits, not a place where lesson content is kept.
- **A student's code is not stored** by codeexec-service. The browser keeps an unsent draft per lesson in `localStorage` and nothing else.
- **The editor is a plain text box** — no syntax highlighting. Adding CodeMirror later would replace one component, `practice-editor.tsx`.
- **Swapping the sandbox** means writing another implementation of `CodeRunner` (`Judge0CodeRunner`, `PistonCodeRunner`), plus one line in `CodeExecConfig`; nothing else changes.
