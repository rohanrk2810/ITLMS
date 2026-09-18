# Running IT-ILMS locally

Everything runs in Docker: PostgreSQL, Kafka, Redis, the LiveKit media server, a mail catcher, and the services themselves.

## Before you start

| Tool | Version | Why |
|---|---|---|
| JDK | 21 or later | The services are built on the host, not inside Docker |
| Maven | 3.9+ | Or use `./mvnw` if you prefer the wrapper |
| Docker Desktop | current | Give it **at least 6 GB** of memory (Settings → Resources) |
| Node.js | 20+ | For the helper scripts, and later the frontend |

The full stack is about **4.5 GB of memory** and 13 containers. On a 16 GB machine, close what you can before starting it — an IDE, a browser with many tabs and a local database server together can leave Docker short, and the symptom is containers being killed rather than a clear error.

---

## First run

### 1. Build the jars

```bash
mvn -DskipTests package
```

Images package a jar that Maven has already produced, so this step comes first. Run it again after any code change.

### 2. Create the environment file

```bash
cd deploy/docker
cp .env.example .env
```

Then replace every `CHANGE_ME` in `.env`:

```bash
openssl rand -base64 48      # JWT_SECRET
openssl rand -hex 32         # POSTGRES_PASSWORD and LIVEKIT_API_SECRET
```

`BOOTSTRAP_ADMIN_PASSWORD` is the first administrator's password: at least 8 characters, with an upper-case letter, a lower-case letter and a digit.

`.env` is git-ignored. Nothing in the repository contains a working secret, and no service starts without one — a missing `JWT_SECRET` or database password stops the container rather than falling back to a default.

### 3. Start everything

```bash
docker compose up -d --build
docker compose ps
```

The first start takes a few minutes: PostgreSQL creates twelve databases, Kafka formats its storage, and each service applies its own migrations. Wait until every row reads `healthy`.

### 4. Sign in

```bash
curl -s localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"identifier":"admin@itinstitute.local","password":"<BOOTSTRAP_ADMIN_PASSWORD>"}'
```

The response carries an access token. In Swagger UI, paste it into **Authorize**.

---

## What runs where

| Address | What |
|---|---|
| http://localhost:8080 | API gateway — every request goes here |
| http://localhost:8080/swagger-ui.html | Swagger, with every service in one page |
| http://localhost:8761 | Eureka: which services have registered |
| http://localhost:8888 | Config server (serves `config-repo/`) |
| http://localhost:8025 | Mailpit: every email the system sends |
| localhost:5433 | PostgreSQL (5433, because a locally installed Postgres usually holds 5432) |
| localhost:9092 | Kafka |
| ws://localhost:7880 | LiveKit, for live classes |

Service ports (8081–8091) are deliberately **not** published. Everything reaches them through the gateway, exactly as in production.

---

## Checking that it works

```bash
node deploy/scripts/check-gateway-routes.mjs   # every endpoint reaches its own service
node deploy/scripts/smoke-test.mjs             # a full run through the system
```

The smoke test signs in as the administrator, creates a course, a trainer and students, opens an online batch, schedules a live class, joins it as a student and as the trainer, replays LiveKit's webhooks, and then checks that the attendance register was written correctly. It also checks the security boundaries: no token, a student reaching for another student's data, an unsigned webhook.

---

## Day-to-day

```bash
docker compose logs -f batch-service          # follow one service
docker compose restart liveclass-service      # restart one
docker compose up -d --build course-service   # after rebuilding its jar
docker compose down                           # stop everything, keep the data
docker compose down -v                        # stop everything and erase the databases
```

Open a database:

```bash
docker compose exec postgres psql -U itilms -d itilms_batch
```

Configuration lives in `config-repo/` and is mounted into the config server, so editing a file there needs no rebuild — restart the affected service.

### Running one service from your IDE

The defaults in `config-repo/` point at `localhost`, so a service started from an IDE finds the containers without any change:

```bash
docker compose up -d postgres kafka redis livekit mailpit discovery-server config-server api-gateway
docker compose stop batch-service        # stop the containerised copy
```

Then run `BatchServiceApplication` from the IDE with `DB_PASSWORD`, `JWT_SECRET` and `DB_PORT=5433` set in the run configuration. It registers with Eureka and the gateway routes to it.

---

## Live classes

Live classes need the browser to reach LiveKit directly, which by default means `ws://localhost:7880` — fine on the machine running Docker.

To join from a phone or another laptop on the same network, set three values in `.env`:

```dotenv
BIND_ADDRESS=0.0.0.0
LIVEKIT_NODE_IP=192.168.1.42        # this machine's LAN address
LIVEKIT_WS_URL=ws://192.168.1.42:7880
```

Then `docker compose up -d`. Note that `BIND_ADDRESS=0.0.0.0` also exposes PostgreSQL and Kafka to the network, so use it on a trusted network only.

Browsers refuse camera and microphone access on a plain HTTP page served from anything other than `localhost`, so testing from another device needs HTTPS in front of the frontend.

---

## When something goes wrong

**A container keeps restarting.** `docker compose logs <service> | grep -A5 "APPLICATION FAILED\|Caused by"`. The usual causes are a missing secret in `.env` and a database whose schema no longer matches the entities.

**`port is already allocated`.** Something on the host holds that port. Change the matching `*_HOST_PORT` in `.env`.

**Docker stops responding, or containers are killed.** Docker has run out of memory. Raise its limit in Docker Desktop settings, or start fewer services.

**An image fails to download** with `EOF` or `failed to copy`. A network problem, not a configuration one — `docker pull <image>` again; each attempt keeps what it already fetched.

**`Migration checksum mismatch`.** A migration that has already run was edited. During development, reset that one service's database:

```bash
docker compose stop batch-service
docker compose exec postgres psql -U itilms -d itilms_batch \
  -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public AUTHORIZATION itilms;"
docker compose start batch-service
```

Once a migration has run anywhere but a development machine, add a new migration instead of editing the old one.

**New students and trainers cannot sign in.** Their temporary password is sent as a notification, and notification-service is not built yet — until then, the password only exists inside the Kafka event. For local testing, `deploy/scripts/smoke-test.mjs` shows how to set a known password directly.
