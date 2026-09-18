# Documentation Review

**Source:** `IT_Institute_LMS_Complete_Project_Documentation.pdf` (24 sections)
**Reviewed against:** the implementation in this repository
**Last updated:** 2026-09-18

This document records every place where IT-ILMS, as built, differs from the source document, and why. Section numbers (§) refer to the source document.

Entries fall into five groups:

| Group | Meaning | IDs |
|---|---|---|
| [Decisions](#1-decisions-that-change-the-design) | The product owner chose a different design from the one the document specifies | D1–D6 |
| [Resolutions](#2-contradictions-and-ambiguities) | The document contradicts itself or is unclear; the chosen reading is recorded | R1–R10 |
| [Additions](#3-data-the-document-does-not-model) | Tables the requirements need but §10 does not list | A1–A7 |
| [Open questions](#4-open-questions-for-modules-not-yet-built) | To settle before the affected module is built | O1–O6 |
| [Known limitations](#5-known-limitations) | Behaviour that is weaker than the document asks for, with a planned fix | L1–L2 |

[Section 6](#6-traceability-security-and-business-rules) traces the security requirements (§12) and business rules (§14) to where they are enforced, and [section 7](#7-defects-found-and-fixed-during-this-review) lists defects found in the implementation while carrying out this review.

---

## 1. Decisions that change the design

These override the document. Each was an explicit request from the product owner.

| ID | Document says | Built instead |
|---|---|---|
| **D1** | One layered Spring Boot application, `lms-backend` (§9, §20.1) | **Microservices:** 12 business services behind Spring Cloud Gateway, with Eureka for discovery, Spring Cloud Config for configuration, and Kafka for events between services |
| **D2** | One relational schema (§10); one `DB_URL` (§19.1) | **A database per service** (12 PostgreSQL databases). A service never reads another service's tables |
| **D3** | Live classes are a future enhancement "via Zoom/Google Meet" (§22), to be added only after the MVP (§24) | **Live classes are a core module**, on a self-hosted LiveKit media server. Attendance for online sessions is computed from time spent in the room |
| **D4** | React with TypeScript or JavaScript, Material UI or Bootstrap (§24); `App.jsx` (§20.2) | React + **TypeScript** + **Tailwind CSS** + **shadcn/ui** |
| **D5** | Docker Compose with Nginx (§19) | Docker Compose now. Nginx arrives with the frontend. Kubernetes manifests follow later |
| **D6** | Repository with `lms-backend/`, `lms-frontend/` and `docs/` (§20.3) | `services/`, `infrastructure/`, `config-repo/`, `deploy/`, `docs/`; `lms-frontend/` to follow |

### What D1 and D2 change in practice

- **No foreign keys between services.** A batch refers to a course by id, but PostgreSQL cannot enforce that the course exists, because the course lives in another database. The owning service checks references when it writes. For example, batch-service confirms the course exists before it creates a batch.
- **Some data is copied, and copies can lag.** Where a service needs another service's data to answer a query in one step, it keeps a copy and updates it from events. An example is batch-service holding each course's title. A renamed course shows its old name in batch listings for a few seconds.
- **Dashboards and reports are eventually consistent** (§15). reporting-service builds them from events. Acceptance scenario 3 in §18.3 ("student immediately sees updated attendance") is still met, because the student's attendance page reads from batch-service directly.
- **The audit log (§10 `audit_logs`) is collected centrally.** Every service publishes an audit event, and reporting-service stores them in one chronological log.

### What D3 adds

- **Rooms.** A LiveKit room is created ahead of each online or hybrid session. Rooms exist only for scheduled classes: LiveKit is configured to refuse any other room.
- **Entry.** Only students actively enrolled in the batch, trainers of the batch, and staff can obtain a join token. The token decides what each person can do in the room, and the server signs it.
- **Attendance.** Time in the room is added up across reconnects and counted only within the scheduled slot. It is then compared with the length of the class as actually taught. At 70% or more the student is PRESENT, at 40% or more LATE, and below that ABSENT. Both thresholds are configurable.
  - A trainer's manual correction always takes precedence over the computed result.
  - If no trainer or staff member ever joined, no attendance is published, because the class did not take place.

---

## 2. Contradictions and ambiguities

| ID | The problem | Resolution |
|---|---|---|
| **R1** | §10 stores a `role` on `users` and also lists a separate `roles` table. The two cannot both be the source of truth. | One role per user, stored on the user. There is no `roles` table: every role in §4 is a fixed job function, and no screen in §8.4 manages roles. |
| **R2** | §4 names "SUPER ADMIN / ADMIN" but never says how the two differ. | A single `ADMIN` role. |
| **R3** | §4 gives one role, "PLACEMENT / COUNSELOR", responsibility for both admissions and placements, while §6.4 assigns each lead a "counselor". | The `PLACEMENT` role manages leads alongside `ADMIN` and `COORDINATOR`. Only `ADMIN` and `COORDINATOR` see every overdue follow-up across counsellors. |
| **R4** | A student's place in a batch is modelled twice, in `batch_students` and in `enrollments` (§10, §10.1). | `enrollments` only. §14's "not enrolled twice in the same active batch" is enforced by a partial unique index on active enrolments. |
| **R5** | §11 allows student self-registration only "where enabled", but §7.1 creates the student account after an admission is confirmed. | Setting `itilms.security.self-registration-enabled`, **off by default**. When off, accounts come from the admission workflow. |
| **R6** | §6.1 allows login by email *or phone*, but §10 does not make `users.phone` unique, so a phone number could match two accounts. | Phone numbers are unique. |
| **R7** | §7.3 completes a course when the *required* lessons, tests and assignments are done, but nothing in §10 marks anything as required. | Lessons carry `is_mandatory`, and only mandatory lessons count toward completion. Tests and assignments carry the same flag; completion counts only mandatory ones. |
| **R8** | §6.9 lists present, absent and late, which leaves no way to record an approved absence. | `EXCUSED` is added. It is left out of the percentage entirely, so an approved absence does not cost a student their certificate (§7.3). |
| **R9** | §3.1 scopes "online/offline attendance" without saying how online attendance differs. | Classroom sessions are marked by the trainer. Online sessions are computed from room time (D3), and the trainer can override the result. |
| **R10** | §6.9 and §14 require corrections to be audited, but the `attendance` table in §10 has nowhere to record one. | `corrected_at` and `corrected_by` columns were added. Changing a saved register requires a reason, which is recorded in the audit log. |

---

## 3. Data the document does not model

These tables were added because the requirements cannot be met without them.

| ID | Table(s) | Service | Needed by |
|---|---|---|---|
| **A1** | `refresh_tokens`, `password_reset_tokens` | identity | §12 refresh strategy; §8.1 forgot/reset password |
| **A2** | `lead_followups` | admission | §6.4 follow-up history. A single `next_follow_up_at` on `leads` loses every earlier conversation. |
| **A3** | `trainer_courses` | admission | §6.3 "assign subjects/courses" |
| **A4** | `student_documents` | admission | §6.2 "document references" |
| **A5** | `batch_trainers` | batch | §6.6 "trainer(s)". §10 `batches.trainer_id` allows only one trainer per batch. |
| **A6** | `live_sessions`, `live_participants`, `live_participant_events` | liveclass | D3 |
| **A7** | Copies of other services' data, such as `course_enrollments` in course-service | several | D2. Each copy is kept current from events. |

---

## 4. Open questions for modules not yet built

Each question has a proposed answer. If nobody objects, the proposal will be built.

| ID | Module | Question | Proposal |
|---|---|---|---|
| **O1** | Fees | §10 ties each payment to exactly one installment (`payments.fee_installment_id`). How are partial payments, one payment covering two installments, and advance payments recorded? | Record the payment against the fee plan and allocate it to installments oldest-due first. §14's outstanding formula (net fee minus successful payments) works unchanged. **Built as proposed.** |
| **O2** | Fees | §10 gives installments no paid, partly-paid or overdue status. | Derive the status from payments at read time rather than storing it, so the two can never disagree. **Built as proposed**, including the overdue amount, which is the unpaid part of an installment rather than its full value. |
| **O3** | Tests | `quiz_questions` has four fixed option columns (`option_a`–`option_d`), but §5 asks for an "MCQ/coding-ready framework". | Store options in a separate table, and add a question type. MCQ ships first. **Built as proposed:** single-choice, multi-choice and true/false. |
| **O4** | Assignments | `assignment_submissions` holds one `file_url`, but §6.10 lets students submit "files/text". Resubmission is not addressed. | A text answer plus any number of attachments through file-service. Resubmission is allowed until the deadline, and the latest submission is the one evaluated. **Built as proposed**, with one addition: once work is marked, only the trainer can reopen it, by returning it for rework. |
| **O5** | Certificates | §6.13 requires public verification by certificate number, while §17 requires certificate URLs to be non-guessable. With sequential numbers, anyone can walk the verification page and collect every graduate's name. | The verification page shows only name, course and issue date. It is rate-limited, and each certificate also carries a random verification code, so a guessed number reveals nothing without the code. |
| **O6** | Students | §6.2 asks for student import but gives no format. | A CSV template, with a dry run that reports every invalid row before anything is saved. |

---

## 5. Known limitations

| ID | Limitation | Planned fix |
|---|---|---|
| **L1** | §4.1 limits trainers to their own batches. In course-service, a trainer can read progress for *any* batch, because course-service does not know which trainer teaches which batch (that is batch-service's data). | Check the trainer's batches through batch-service, the same way liveclass-service already does. |
| **L2** | In admission-service, any trainer can open any student's profile by id, where §4.1 limits them to "own batch view". | The same batch-service check as L1. |

---

## 6. Traceability: security and business rules

### §12 Security requirements

| Requirement | Where it is enforced | Status |
|---|---|---|
| JWT with short-lived access token and refresh strategy | identity-service issues a 30-minute access token and a 7-day refresh token. The refresh token rotates on every use; reusing an old one revokes the whole token family. | Done |
| BCrypt hashing, no plain-text passwords | identity-service, BCrypt strength 12 | Done |
| Role-based authorization at API and service level | `@PreAuthorize` on every endpoint, plus ownership checks in services (a student reads only their own records) | Done for implemented services; see L1, L2 |
| No reliance on frontend visibility | The gateway validates every token, and each service validates it again | Done |
| Validate uploads; restrict MIME type and size | file-service, with an allow-list configured in `config-repo/file-service.yml` | Pending (file-service) |
| HTTPS in production | Terminated at the reverse proxy | Pending (deployment) |
| No passwords, tokens or payment data in logs | Refresh and reset tokens are stored only as SHA-256 hashes, so neither the database nor a log line built from it holds a usable token. A log review is part of the release checklist. | Partly done |
| Secrets from the environment | The database password, JWT secret, first admin password and LiveKit secret have no defaults; startup fails if any is missing | Done |
| Secure CORS and security headers | Allowed origins are configured at the gateway and in every service | Done |
| Audit privileged actions | Services publish audit events; reporting-service stores them | Publishing done; storage pending (reporting-service) |
| Rate-limit login and password reset | Gateway limiter backed by Redis on `/api/auth/**`: 5 requests per second, bursts of 10 | Done |

### §14 Validation and business rules

| Rule | Where it is enforced | Status |
|---|---|---|
| Only authorized staff modify master data | `@PreAuthorize` with the STAFF and ADMIN rules | Done |
| No learning content without enrolment | course-service returns lesson content only for lessons the caller's enrolment unlocks | Done |
| No double enrolment in the same active batch | Partial unique index on `enrollments` | Done |
| Attendance corrections by authorized roles, audited | Re-marking a saved register requires a reason and publishes an audit event | Done |
| Late assignment submissions marked LATE | Decided from the server clock at submission; a trainer can instead refuse late work per assignment | Done |
| Quiz score calculated on the server | Scored from the stored answer key. The paper a student receives has no answer key in it, and answers naming another question's options are refused | Done |
| Certificate criteria validated on the server | certificate-service | Pending |
| Outstanding = net fee − successful payments | Computed on every read from the payments, never stored; reversed payments do not count | Done |
| Course not published without required metadata | `publish` refuses and names each missing field | Done |
| Trainers see only their batches' submissions | Every read and every mark checks with batch-service that the trainer teaches the batch | Done |
| Placement status changes audited | placement-service | Pending |

---

## 7. Defects found and fixed during this review

Comparing the implementation against §12 and §14 turned up four defects. All four are fixed.

| Defect | Effect before the fix | Fix |
|---|---|---|
| `GET /api/progress/students/{studentId}/courses/{courseId}` did not check ownership | Any signed-in student could read another student's course progress by changing the id in the URL | The service now applies the same ownership check that attendance uses |
| Self-registration was always open | Anyone on the internet could create a student account and a student profile (R5) | Off by default, behind a setting |
| Internal service-to-service endpoints (`/internal/...`) were reachable through the gateway | A signed-in student could call lookups meant for other services, such as resolving batch or course ids in bulk | The gateway answers 404 for any `/internal/` path |
| A LiveKit room closing early (unused before class, or everyone disconnected mid-class) was treated as the class ending | Attendance was published early, and every later join was refused with "this class has finished" | Only a room that was in use and closes near the scheduled end settles the class; any other closure lets the room be recreated on the next join |
