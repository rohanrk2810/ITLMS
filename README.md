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
| **Practice code editor** | Run code inside lessons in a sandbox (Judge0 or Piston), with code analysis ([docs/08](docs/08-code-execution.md), [docs/16](docs/16-code-analysis.md)) |
| **Test portal** | Timed tests with secure test mode, and a camera check with a face oval, same-face and phone detection ([docs/09](docs/09-test-portal.md)), and student progress reports ([docs/10](docs/10-student-progress-report.md)) |
| **Live-class extras** | Recording and content protection, live monitoring (camera compulsory where it is on), optional microphone ([docs/11](docs/11-live-recording-and-content-protection.md), [docs/15](docs/15-live-class-monitoring.md), [docs/18](docs/18-microphone-option.md)); subtitles and a text transcript of what the trainer said, an End class button, and students who cannot see each other ([docs/23](docs/23-live-class-subtitles-and-ending.md)) |
| **New students and fees** | Staff are told of a new student; a student sees only the course list until placed; course fee set by staff and shown before a request; the self-registration switch ([docs/24](docs/24-new-students-and-fees.md)) |
| **Load testing** | k6 scripts for a class signing in and taking a test together, what they found and fixed, and the results ([docs/25](docs/25-load-testing.md)) |
| **Class delivery options** | Per session: built-in live room, embedded Jitsi, or a Google Meet / Zoom link ([docs/20](docs/20-class-delivery-options.md)) |
| **Video lesson monitoring** | Opt-in per lesson: camera check with a face oval, auto-pause when the face is missing, completion only by watching 90% (no Mark as complete button), and who-watched-how-much ([docs/21](docs/21-video-lesson-monitoring.md)) |
| **Human check (captcha)** | Optional Cloudflare Turnstile on sign-in, registration and forgot-password, verified on the server ([docs/22](docs/22-human-check-captcha.md)) |
| **Accounts and branding** | Single active login per user, admin-set branding and sign-in background, language choice ([docs/12](docs/12-single-active-login.md), [docs/13](docs/13-branding.md), [docs/17](docs/17-language-choice.md)) |

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

1. **Get the code and install the tools:**
   ```bash
   git clone https://github.com/rohanrk2810/ITLMS.git
   cd ITLMS
   ```
   Needs JDK 21+, Maven 3.9+, Docker Desktop (give it **at least 6 GB** memory in Settings → Resources), Node.js 20+.
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

Full instructions — running a single service from an IDE, live classes over a LAN, and what to do when something breaks — are in **[docs/05-local-setup.md](docs/05-local-setup.md)**. Taking this beyond one developer's machine: the step-by-step server process is in [Deploying to a server](#deploying-to-a-server-step-by-step) below, and the reasoning (reverse proxy/HTTPS, real email, MinIO, resource sizing, backups) is in **[docs/07-deployment-guide.md](docs/07-deployment-guide.md)**.

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

## Deploying to a server (step by step)

This is the exact process used to put IT-ILMS on a cloud server (an Azure Linux VM). The same steps work on any Ubuntu server. Replace `<SERVER_IP>` with the server's public IP address and `<YOU>` with your own values. **Never paste a password, a key, or a whole screen of command output into the terminal or into a chat.**

### What the server needs

| | |
|---|---|
| Machine | Ubuntu 24.04 (or 22.04), **x86_64**, at least **2 vCPU and 8 GiB RAM** (the stack uses about 4.5 GiB), 64 GiB disk. Azure size used: `Standard_B2as_v2`. |
| Ports open to the internet | **22** (SSH), **80** and **443** (web), **7881 TCP** and **7882 UDP** (live-class video). In Azure these are *inbound port rules* on the VM's network security group. UDP 7882 is a separate rule from TCP 7881. |
| Address | Camera and microphone only work on **HTTPS**, so the site needs a host name. With no domain of your own, `app.<SERVER_IP>.sslip.io` works for testing (sslip.io turns the IP into a name). For real use, buy a domain and point `app.`, `api.` and `live.` records at the server. |
| Not set up on this server | Live-class recording (LiveKit Egress) needs its own configuration; leave it stopped (step 7). The code editor's sandbox (Piston) is optional and needs step 7b; an ARM server cannot run it, an x86 server can. |

### 1. SSH key (on your own computer, PowerShell)

```powershell
ssh-keygen -t ed25519 -f $env:USERPROFILE\.ssh\itilms_server
Get-Content $env:USERPROFILE\.ssh\itilms_server.pub     # paste this PUBLIC key into the cloud's "create VM" form
```

Log in with `ssh -i $env:USERPROFILE\.ssh\itilms_server azureuser@<SERVER_IP>`. The private key (the file without `.pub`) never leaves your computer.

### 2. Install the tools (on the server)

```bash
sudo apt update && sudo apt install -y git curl openjdk-21-jdk
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker azureuser          # then log out and back in (exit, ssh again)
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile && echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
cd /tmp && curl -O https://archive.apache.org/dist/maven/maven-3/3.9.9/binaries/apache-maven-3.9.9-bin.tar.gz && sudo tar xzf apache-maven-3.9.9-bin.tar.gz -C /opt && sudo ln -s /opt/apache-maven-3.9.9/bin/mvn /usr/local/bin/mvn && cd ~
```

Check: `docker ps` runs without `sudo`, `mvn -version` shows Maven 3.9.9 and Java 21, `free -h` shows 4 GiB of swap.

### 3. Get the code and build it

```bash
cd ~ && git clone https://github.com/rohanrk2810/ITLMS.git && cd ITLMS
nohup mvn -DskipTests clean package > ~/build.log 2>&1 &
tail -3 ~/build.log                        # repeat until it shows BUILD SUCCESS (a few minutes)
ls ~/ITLMS/services/*/target/*.jar | wc -l # 14
```

Always use `clean`: a stale jar can bundle an old copy of the shared library, and the service then crashes at start.

### 4. Create the server's `.env`

```bash
cd ~/ITLMS/deploy/docker && cp .env.example .env

# three secrets, generated on the server (nobody has to see them)
sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 24)|" .env
sed -i "s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n/+=')|" .env
sed -i "s|^LIVEKIT_API_SECRET=.*|LIVEKIT_API_SECRET=$(openssl rand -hex 32)|" .env
awk -F= '/^(POSTGRES_PASSWORD|JWT_SECRET|LIVEKIT_API_SECRET)=/ {print $1, length($2)}' .env   # lengths about 48, 58, 64; never 0

# the first administrator (8+ characters with upper case, lower case and a digit; letters and digits are safest)
sed -i 's|^BOOTSTRAP_ADMIN_EMAIL=.*|BOOTSTRAP_ADMIN_EMAIL=<YOU>@example.com|; s|^BOOTSTRAP_ADMIN_PASSWORD=.*|BOOTSTRAP_ADMIN_PASSWORD=<A_PASSWORD_YOU_CHOOSE>|' .env

# the addresses (with sslip.io; with a domain use app.<domain>, api.<domain>, live.<domain>)
sed -i 's|^FRONTEND_URL=.*|FRONTEND_URL=https://app.<SERVER_IP>.sslip.io|; s|^LIVEKIT_WS_URL=.*|LIVEKIT_WS_URL=wss://live.<SERVER_IP>.sslip.io|; s|^LIVEKIT_NODE_IP=.*|LIVEKIT_NODE_IP=<SERVER_IP>|; s|^# LIVEKIT_BIND_ADDRESS=.*|LIVEKIT_BIND_ADDRESS=0.0.0.0|' .env
echo 'VITE_API_BASE_URL=https://api.<SERVER_IP>.sslip.io' >> .env

grep -n "^[A-Z_]*=CHANGE_ME" .env          # must print nothing
```

Do not change `BIND_ADDRESS`. Leaving it at `127.0.0.1` keeps the database, Kafka and the mail catcher off the internet; only the reverse proxy and the LiveKit media ports are public.

**Real e-mail (password and reset-code mail only)** with a Gmail address (2-Step Verification on, then an *App password* from <https://myaccount.google.com/apppasswords>):

```bash
cd ~/ITLMS/deploy/docker && sed -i '/^MAIL_/d' .env && printf '%s\n' 'MAIL_ENABLED=true' 'MAIL_NOTIFICATIONS_ENABLED=false' 'MAIL_HOST=smtp.gmail.com' 'MAIL_PORT=587' 'MAIL_USERNAME=<YOU>@gmail.com' 'MAIL_SMTP_AUTH=true' 'MAIL_SMTP_TLS=true' 'MAIL_FROM=<YOU>@gmail.com' 'MAIL_FROM_NAME=<Institute name>' >> .env
read -s -p "Gmail App password: " P && echo && printf 'MAIL_PASSWORD=%s\n' "${P// /}" >> .env && unset P
```

**Optional human check** ([docs/22](docs/22-human-check-captcha.md)): create a Turnstile widget at Cloudflare, add the site's host name to it, then:

```bash
cd ~/ITLMS/deploy/docker && sed -i '/^CAPTCHA_/d' .env && printf '%s\n' 'CAPTCHA_ENABLED=true' 'CAPTCHA_SITE_KEY=<the Site Key>' >> .env
read -s -p "Turnstile Secret Key: " S && echo && printf 'CAPTCHA_SECRET=%s\n' "$S" >> .env && unset S
```

### 5. Start everything

```bash
cd ~/ITLMS/deploy/docker
nohup docker compose up -d --build > ~/compose.log 2>&1 &
docker compose ps --format '{{.Name}} {{.Status}}'   # wait until every row says (healthy); 10-15 minutes on 2 vCPU
```

### 6. HTTPS with Caddy

```bash
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https curl gnupg && curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg && curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list && sudo apt update && sudo apt install -y caddy

printf '%s\n' 'app.<SERVER_IP>.sslip.io {' '    reverse_proxy 127.0.0.1:5173' '}' 'api.<SERVER_IP>.sslip.io {' '    reverse_proxy 127.0.0.1:8080' '}' 'live.<SERVER_IP>.sslip.io {' '    reverse_proxy 127.0.0.1:7880' '}' | sudo tee /etc/caddy/Caddyfile && sudo systemctl reload caddy
```

Caddy fetches and renews the HTTPS certificates by itself.

### 7. Stop what this server cannot run

```bash
cd ~/ITLMS/deploy/docker && docker compose stop livekit-egress
```

### 7b. Code editor (Piston sandbox, optional)

Without it the practice editor and coding questions say that running code is not set up. Piston is not started by a plain `docker compose up`; it needs an x86 server with cgroup v2 (Ubuntu 22.04 and 24.04 have it) and about 1.5 GiB of extra RAM while code runs.

```bash
stat -fc %T /sys/fs/cgroup                 # must print cgroup2fs
cd ~/ITLMS/deploy/docker && docker compose --profile piston up -d piston

# install the languages once (a few minutes); they are kept in a Docker volume
docker compose --profile piston exec -T piston node - install java@15.0.2 python@3.12.0 gcc@10.2.0 mono@6.12.0 sqlite3@3.36.0 < ~/ITLMS/deploy/scripts/piston-packages.js

# tell the code-run service to use it
sed -i '/^CODEEXEC_ENGINE=/d; /^PISTON_URL=/d' .env && printf '%s
' 'CODEEXEC_ENGINE=piston' 'PISTON_URL=http://piston:2000' >> .env
docker compose up -d --no-deps codeexec-service
```

Then press **Run** in a lesson's practice editor. Java here is version 15 (no records), and PL/SQL is not supported. Details and limits: [docs/08](docs/08-code-execution.md).

### 8. Check it

```bash
curl -sI https://app.<SERVER_IP>.sslip.io | head -1      # HTTP/2 200
```

Open `https://app.<SERVER_IP>.sslip.io` and sign in with the administrator from step 4. **Change that password straight away** (menu, *Change password*). Then create a test user with your own e-mail and check that the password mail arrives.

### Updating the live server after a code change

On your computer: commit and `git push`. Then on the server, **rebuild and restart only what changed**:

```bash
cd ~/ITLMS && git pull
mvn -q -DskipTests clean package -pl services/identity-service -am     # the service(s) you changed; several: -pl a,b
cd deploy/docker && docker compose up -d --build --no-deps identity-service
```

For the frontend only: `docker compose up -d --build --no-deps frontend`. **Always add `--no-deps`**: without it Docker also recreates the gateway and the platform services, and the whole site is down for a few minutes.

### After the server is stopped and started again

Nothing to run. The containers start by themselves, but 12 Java services take **8-15 minutes** to be ready on 2 vCPU. Signing in earlier shows errors or 502; wait and try again. Check with `docker compose ps --format '{{.Name}} {{.Status}}'`.

### Backups (nothing is automatic)

```bash
mkdir -p ~/backups && for db in $(docker exec itilms-postgres-1 psql -U itilms -At -c "select datname from pg_database where datname like 'itilms_%'"); do docker exec itilms-postgres-1 pg_dump -U itilms "$db" | gzip > ~/backups/$db-$(date +%F).sql.gz; done
```

Copy `~/backups` off the server (for example with `scp`). A deleted VM takes its data with it.

### Stopping the bill

A running cloud VM costs money. When you no longer need it, delete the **resource group** that holds it (Azure portal, *Resource groups*, the group, *Delete*): that removes the VM, disk, IP and network together. Stopping a VM is not enough, its disk and IP keep charging.

### Things that went wrong, and why

| What you see | Cause and fix |
|---|---|
| Courses empty, or a service crashes with `ClassNotFoundException` | The jar was built without `clean`; rebuild with `mvn clean package -pl <service> -am`. |
| Everything is down after changing one service | `--no-deps` was left out; wait for the stack to come back, then use it next time. |
| Sign-in says 500 and the gateway log shows a `NullPointerException` on the client address | Fixed in the gateway (commit `1dde8b5`); update the server. |
| "Please confirm you are not a robot" but no box | The gateway must list `/api/auth/captcha-config` as public (fixed, commit `6870d7e`), and the Turnstile widget must list the site's host name. |
| Live class stuck on "connecting" | UDP **7882** (not just TCP 7881) must be open on the cloud firewall, and `LIVEKIT_NODE_IP` must be the server's public IP. |
| A convert or create fails with 422 "already exists" | The phone number or e-mail is used by another account; on the Users page, *Delete* (a soft delete) frees them. |
| Terminal prints `command not found` for lines of output | You pasted command *output* into the shell. Paste only the command. |
| Windows PowerShell says `&&` is not a valid separator | That command is for the **server** (Linux); run it after `ssh`. |

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
| [docs/08](docs/08-code-execution.md) – [docs/18](docs/18-microphone-option.md) | Feature write-ups: code execution, test portal, progress report, recording, single login, branding, certificate workflow, live monitoring, code analysis, language choice, microphone |
| [docs/19](docs/19-system-design-hld-lld.md) – [docs/25](docs/25-load-testing.md) | System design (HLD/LLD), class delivery options, video-lesson monitoring, human check, subtitles, new students and fees, load testing |
| [docs/26-security.md](docs/26-security.md) | Authentication, authorization, edge limits, data protection, test integrity, audit, known limits |
| [docs/27-design-system.md](docs/27-design-system.md) | Colour tokens, components, layout, forms, theming, accessibility |
| [docs/28-prd.md](docs/28-prd.md) | Product requirements: users, scope with requirement ids, non-functional needs, open questions |
| [docs/29-code-style.md](docs/29-code-style.md) | Backend and frontend conventions, testing, commits, documentation |
| [docs/32-extra-duties.md](docs/32-extra-duties.md) | One person with several jobs (a trainer who also coordinates): who can give it, how it applies at once, what it covers |
| [docs/31-downloads.md](docs/31-downloads.md) | What can be downloaded (students, leads, attendance, results, dashboard, progress reports), who may, and what is logged |
| [docs/30-id-cards.md](docs/30-id-cards.md) | Identity cards for students, trainers and staff: issuing, photos, printing, public check |
| [AGENTS.md](AGENTS.md) | Guide for AI coding agents and new developers: layout, commands, rules that are easy to break |
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

Still open: Kubernetes manifests (Compose first, by design — Doc departure D5), MinIO storage wired in code but not yet exercised end-to-end, no CI image-build/deploy step, no staging environment. Full list: [docs/07-deployment-guide.md § Still not done](docs/07-deployment-guide.md#still-not-done).

## Checks

```bash
mvn test                                        # backend unit tests, every module
node deploy/scripts/check-gateway-routes.mjs    # every endpoint reaches its own service, none is misrouted
node deploy/scripts/smoke-test.mjs              # a full run through a live stack, including security boundaries
cd frontend && npm run build && npm run lint    # frontend build and lint
cd frontend && npm test                          # frontend unit and component tests (Vitest)
cd frontend && npm run e2e                      # browser tests with the API mocked (Playwright; PW_CHANNEL=chrome uses installed Chrome)
```

## Security notes

- The gateway is the only way in from outside; it validates every JWT and strips any identity header a caller tried to inject. Each service still validates the token itself rather than trusting the network it's on.
- `/internal/**` paths (service-to-service only) are refused with a 404 at the gateway before authentication is even checked — they are unreachable from outside regardless of any token.
- Secrets (`JWT_SECRET`, database passwords, LiveKit's API secret) come only from environment variables with no default; a service refuses to start rather than run with a placeholder.
- A temporary password or a password-reset token is only ever emailed — never written to a database column or a log line.

The full security write-up is [docs/26-security.md](docs/26-security.md). Known limitations, tracked deliberately rather than by omission: [docs/02-documentation-review.md](docs/02-documentation-review.md) (see the "Open items" and "Limitations" sections).
