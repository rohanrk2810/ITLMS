# Architecture

IT-ILMS is a set of small Spring Boot services behind one gateway. Each owns a slice of the institute's work and its own database, and they exchange facts over Kafka.

This is a departure from the source document, which specifies a single application (§9, §20.1). See [the documentation review](02-documentation-review.md) for that decision and what it costs.

---

## The shape of it

```
                    browser  (React, to come)
                       │
                       ▼
         ┌──────────────────────────────┐
         │        api-gateway           │   validates the JWT, rate-limits
         │   Spring Cloud Gateway 8080  │   login, refuses /internal/ paths
         └──────────────┬───────────────┘
                        │  lb://service-name
     ┌──────────┬───────┴───────┬───────────┬─────────────┐
     ▼          ▼               ▼           ▼             ▼
 identity   admission        course       batch      liveclass       ... 7 more
   8081       8082            8083         8084         8091
     │          │               │           │             │
     └──────────┴───────────────┴───────────┴─────────────┘
                        │                      │
              own PostgreSQL database     LiveKit media server
                        │
                        ▼
                      Kafka ─── events between services

  discovery-server (Eureka, 8761)   who is running, and where
  config-server (8888)              serves config-repo/ to everyone
  redis                             rate-limit counters for the gateway
```

## The services

| Service | Owns | Built |
|---|---|---|
| **identity** | Accounts, passwords, tokens, roles | Yes |
| **admission** | Leads, follow-ups, student and trainer profiles, documents | Yes |
| **course** | Catalog, modules, lessons, lesson progress | Yes |
| **batch** | Batches, enrolments, timetable, attendance register | Yes |
| **liveclass** | Live rooms, join tokens, room time, automatic attendance | Yes |
| **assessment** | Assignments, submissions, MCQ tests, attempts, results | Yes |
| **finance** | Fee plans, installments, payments, receipts, overdue reminders | Yes |
| **certificate** | Completion checks, issue, PDF, public verification | Yes |
| placement | Companies, jobs, applications, interview stages | Pending |
| notification | In-app and email delivery, announcements | Pending |
| file | Uploads, MIME and size rules, controlled download | Pending |
| reporting | Dashboards, exports, and the audit log store | Pending |

Supporting them: **api-gateway** (the only way in), **discovery-server** (Eureka), **config-server** (serves `config-repo/`), and **common-lib**, a library every service uses for security, error handling and events.

---

## Rules the design follows

**One writer per fact.** Attendance lives in batch-service and nowhere else. A service that needs another's data asks for it or learns it from an event; it never reaches into another database. Twelve databases, twelve owners.

**The gateway is the only door.** It validates the token once, strips any identity headers a caller tried to inject, and adds trusted ones. Each service still validates the token itself: a service must not be safe only because it is behind a gateway.

**Events state facts, not commands.** `itilms.liveclass.attendance-computed` says what a live class turned out to be. batch-service decides what to write in the register. That keeps the attendance rules — thresholds, roster check, manual-override precedence — in the one service that owns attendance.

**Publish after commit.** Events are handed to Kafka only once the database transaction has committed, so no other service ever hears about something that was rolled back.

**Failure is chosen per call.** Every cross-service call names what happens when the other side is down. A missing course title degrades a listing; a failed enrolment check refuses entry to a class. Being unable to prove someone belongs somewhere is not the same as them belonging there.

---

## Who calls whom

Synchronous calls, over Feign, are kept few — they are the ones where the answer must be correct right now:

| From | To | For |
|---|---|---|
| admission | identity | Create the account for a new student or trainer |
| batch | course, admission | Course titles and trainer names when a batch is created |
| liveclass | batch | The session's details, and whether a student is actually enrolled |
| assessment | batch | Whether a student is in the batch a test is set for, and whether a trainer teaches it |
| finance | admission | The student's name and code when a fee plan is raised by hand |
| certificate | course, assessment, batch, finance, admission | Each condition of the completion rule, from the service that owns it, at the moment of issue |

## What travels over Kafka

| Event | Published by | Consumed by | So that |
|---|---|---|---|
| `user-created` | identity | admission | A self-registered student gets a profile |
| `student-admitted` | admission | finance | The fee agreed at admission becomes a fee plan without being typed in twice |
| `profile-linked` | admission | identity | Access tokens can carry the profile id, with no lookup at login |
| `enrollment-created` / `-closed` | batch | course | Progress rows exist the moment a student is enrolled |
| `course.published` | course | batch | Batch listings show the current course title |
| `session-scheduled` / `-rescheduled` | batch | liveclass | A live room is ready before the class starts |
| `session-cancelled` | batch | liveclass | A called-off class does not leave an open room |
| `live.attendance-computed` | liveclass | batch | An online class writes its own register |
| `assignment-created`, `submission-evaluated`, `quiz-attempt-completed` | assessment | notification, reporting, certificate (to come) | Results reach dashboards and the completion check without anyone asking assessment-service |
| `fee-plan-created`, `payment-recorded`, `installment-overdue` | finance | notification, reporting (to come) | Students hear about dues and receipts; the dashboard sees collections |
| `notification.requested` | any | notification | One way to reach people, from anywhere |
| `audit.recorded` | any | reporting | One chronological audit log across twelve databases |

Topics are created at startup from `KafkaTopics`, with three partitions each, keyed so that everything about one class or one student keeps its order.

---

## A request, end to end

A student clicks **Join** on tonight's class:

1. The gateway checks the token, and passes the caller's id, role and profile id to liveclass-service.
2. liveclass-service finds the room record for that session — created earlier from the scheduling event.
3. It asks batch-service whether this student holds an active place in the batch. No answer means no entry.
4. It asks LiveKit to create the room if it is not already there, and mints a token whose grants match the caller's role. A student's token cannot be edited into a trainer's: the server signs the grants.
5. The browser connects to LiveKit directly. Media never passes through IT-ILMS.
6. LiveKit reports each join and leave to liveclass-service, signed. Time in the room accumulates per person.
7. When the class ends, liveclass-service works out each student's share of it and publishes the result.
8. batch-service writes the register, marking anyone who never appeared absent — and leaves any mark the trainer made by hand untouched.

---

## Cross-cutting pieces

**common-lib** carries what every service would otherwise repeat: JWT verification and the `AppPrincipal`, the deny-by-default security chain, one error contract, the event publisher, auditing columns, and code generators for student, batch and certificate numbers. It auto-configures itself, so a new service picks all of it up by adding the dependency.

**Configuration** lives in `config-repo/`, one file per service plus a shared `application.yml`. Secrets are not in it: files reference environment variables, and a service with no `JWT_SECRET` or database password refuses to start rather than falling back to a default.

**Identity in a token.** The access token carries user id, role, and the caller's student or trainer id. No service queries identity-service to find out who is calling. The profile id gets there through `profile-linked`, which is why login never makes a cross-service call.

---

## What this costs

Worth being honest about, since the document assumed one application:

- **Twelve databases means no foreign keys between services.** The owning service checks a reference before writing; the database cannot.
- **Some data is copied and can lag** by seconds — a renamed course, a changed display name.
- **Dashboards are eventually consistent.** Anything a student sees about themselves is read from the owning service and is immediate.
- **More to run.** Thirteen containers for a working system, against three in the original design.

The gain is that batch-service can be deployed during a fee-collection drive without touching finance, that a live class with a hundred students cannot slow the admission desk, and that each service can be scaled where it actually hurts.
