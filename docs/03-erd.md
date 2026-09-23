# Entity-Relationship Reference

Twelve databases, one per service (Doc departure D2 — see [documentation review](02-documentation-review.md)). There is **no cross-database foreign key anywhere in this system**: a column that names another service's row (`course_id`, `student_id`, `batch_id`, ...) is a plain `BIGINT` the owning service checks itself, never a PostgreSQL `REFERENCES`. Within one service's own database, real foreign keys are used freely.

This document lists every table, service by service, as built (Flyway `V1__*.sql` under each `services/<name>/src/main/resources/db/migration/`). For *why* a table exists beyond the source PDF, see the [A1–A10 entries](02-documentation-review.md#3-data-the-document-does-not-model) in the documentation review — this file only cross-references those IDs, it does not repeat the reasoning.

Legend: **PK** primary key, **FK →** foreign key to another table in the *same* database, *cross-svc* a same-shaped id column with no database-level constraint, pointing at another service.

---

## identity-service — `itilms_identity`

Owns accounts, credentials, sessions. Does **not** own student/trainer profiles (admission-service).

### `users`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| first_name, last_name | VARCHAR(80) | |
| email | VARCHAR(160) | UNIQUE, plus a case-insensitive `lower(email)` unique index — login accepts email or phone |
| phone | VARCHAR(20) | UNIQUE |
| password_hash | VARCHAR(100) | BCrypt |
| role | VARCHAR(30) | CHECK: ADMIN, COORDINATOR, TRAINER, STUDENT, PLACEMENT, FINANCE |
| status | VARCHAR(20) | CHECK: ACTIVE, INACTIVE, BLOCKED |
| profile_id, profile_code | BIGINT, VARCHAR(30) | *cross-svc* → admission-service's `students`/`trainers`. Written only from `ProfileLinkedEvent`, never from a request |
| failed_attempts, locked_until | INTEGER, TIMESTAMPTZ | brute-force lockout, cleared on successful sign-in |
| must_change_password | BOOLEAN | |
| last_login_at, created_at, updated_at, created_by, updated_by | | |

### `refresh_tokens`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| user_id | BIGINT | **FK →** users.id, `ON DELETE CASCADE` |
| token_hash | VARCHAR(100) | UNIQUE — SHA-256 hash only, never the raw token |
| expires_at, revoked_at, replaced_by | | `replaced_by` is the rotation chain: reusing a revoked token revokes the whole family |
| user_agent, ip_address, created_at | | |

### `password_reset_tokens`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| user_id | BIGINT | **FK →** users.id, `ON DELETE CASCADE` |
| token_hash | VARCHAR(100) | UNIQUE, hashed like refresh tokens |
| expires_at, used_at | | `used_at` makes the token single-use |
| requested_ip, created_at | | |

---

## admission-service — `itilms_admission`

Owns leads, student and trainer profiles, and their documents. `user_id` columns are *cross-svc* → identity-service, indexed and unique but not FK-constrained.

### `leads` (Doc S6.4)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| name, phone, email | | |
| source | VARCHAR(40) | CHECK: WALK_IN, WEBSITE, REFERRAL, PHONE, SOCIAL_MEDIA, CAMPAIGN, SELF_REGISTRATION, OTHER |
| interested_course_id | BIGINT | *cross-svc* → course-service |
| counselor_user_id | BIGINT | *cross-svc* → identity-service |
| status | VARCHAR(30) | CHECK: NEW, CONTACTED, FOLLOW_UP, INTERESTED, NOT_INTERESTED, CONVERTED, LOST |
| next_follow_up_at, notes, lost_reason | | |
| converted_student_id | BIGINT | **FK →** students.id (added after `students` exists); CHECK ties this pair to `status = CONVERTED` |
| converted_at, created_at, updated_at, created_by, updated_by | | |

### `lead_followups` — A2
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| lead_id | BIGINT | **FK →** leads.id, `ON DELETE CASCADE` |
| contacted_at, outcome, remark, next_action_at, created_by, created_at | | `outcome` CHECK: CALLED, NO_ANSWER, VISITED, EMAILED, MESSAGED, MEETING, OTHER |

### `students` (Doc S6.2)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| user_id | BIGINT | UNIQUE, *cross-svc* → identity-service |
| student_code | VARCHAR(30) | UNIQUE, e.g. `STU-2026-000123` |
| full_name, email, phone | | denormalised from identity-service via `UserUpdatedEvent` |
| date_of_birth, gender, highest_education, college, graduation_year, address_line, city, state, pincode, guardian_name, guardian_phone, emergency_contact | | |
| admission_date, admission_source | | |
| status | VARCHAR(20) | CHECK: ACTIVE, ALUMNI, DROPPED, SUSPENDED |
| remarks, created_at, updated_at, created_by, updated_by | | |

### `trainers` (Doc S6.3)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| user_id | BIGINT | UNIQUE, *cross-svc* → identity-service |
| employee_code | VARCHAR(30) | UNIQUE |
| full_name, email, phone, specialization, qualification, experience_years, bio, joined_on | | |
| status | VARCHAR(20) | CHECK: ACTIVE, INACTIVE, ON_LEAVE |
| created_at, updated_at, created_by, updated_by | | |

### `trainer_courses` — A3
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| trainer_id | BIGINT | **FK →** trainers.id, `ON DELETE CASCADE` |
| course_id | BIGINT | *cross-svc* → course-service |
| created_at | | UNIQUE (trainer_id, course_id) |

### `student_documents` — A4
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| student_id | BIGINT | **FK →** students.id, `ON DELETE CASCADE` |
| document_type | VARCHAR(40) | CHECK: ID_PROOF, ADDRESS_PROOF, PHOTO, MARKSHEET, DEGREE, RESUME, OTHER |
| file_ref | VARCHAR(64) | *cross-svc* → file-service's `files.id` |
| file_name, verified, verified_by, verified_at, uploaded_at | | |

---

## course-service — `itilms_course`

Owns the catalog, curriculum and per-lesson progress. Progress lives here (not batch-service) because it is measured against lessons, which are this service's data.

### `courses`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| title, code, summary, description, learning_outcomes, prerequisites, technology_stack | | code UNIQUE |
| duration_hours, level, fee, thumbnail_ref | | level CHECK: BEGINNER, INTERMEDIATE, ADVANCED |
| status | VARCHAR(20) | CHECK: DRAFT, PUBLISHED, ARCHIVED; a CHECK also blocks publishing without description/summary/published_at |
| published_at, created_at, updated_at, created_by, updated_by | | |

### `course_modules`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| course_id | BIGINT | **FK →** courses.id, `ON DELETE CASCADE` |
| title, description, sequence_no | | UNIQUE (course_id, sequence_no) |
| created_at, updated_at, created_by, updated_by | | |

### `lessons`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| module_id | BIGINT | **FK →** course_modules.id, `ON DELETE CASCADE` |
| title, type | | type CHECK: VIDEO, PDF, NOTE, LINK, TEXT |
| content_url, content_file_ref, text_content | | CHECK requires at least one non-empty |
| duration_minutes, sequence_no | | UNIQUE (module_id, sequence_no) |
| is_preview | BOOLEAN | readable without enrolment (Doc S6.8/S8.1) |
| is_mandatory | BOOLEAN | only mandatory lessons count toward completion (R7) |
| created_at, updated_at, created_by, updated_by | | |

### `course_enrollments` — A7 (mirror)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| enrollment_id | BIGINT | UNIQUE, *cross-svc* → batch-service's `enrollments.id` (source of truth) |
| student_id, user_id | BIGINT | *cross-svc* → admission/identity |
| course_id | BIGINT | **FK →** courses.id |
| batch_id | BIGINT | *cross-svc* → batch-service |
| status | VARCHAR(20) | CHECK: ACTIVE, COMPLETED, DROPPED, SUSPENDED |
| progress_percent, completed_lessons, total_lessons, completed_at, enrolled_at | | populated from `EnrollmentCreatedEvent` |

### `lesson_progress` (Doc S7.2)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| enrollment_id | BIGINT | **FK →** course_enrollments.id, `ON DELETE CASCADE` |
| lesson_id | BIGINT | **FK →** lessons.id, `ON DELETE CASCADE` |
| completed, watched_seconds, completed_at, updated_at | | UNIQUE (enrollment_id, lesson_id) |

---

## batch-service — `itilms_batch`

Owns batches, enrolment, the timetable and the attendance register. **Design departure:** the PDF's §10 lists both `batch_students` and `enrollments`; only `enrollments` is built here (see [R4](02-documentation-review.md#2-contradictions-and-ambiguities)).

### `batches`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| batch_code | VARCHAR(30) | UNIQUE |
| name, course_id, course_code, course_title | | course_id *cross-svc* → course-service; code/title denormalised |
| trainer_id, trainer_user_id, trainer_name | | *cross-svc* → admission/identity; nullable (batch often scheduled before allocation) |
| start_date, end_date, start_time, end_time, class_days | | CHECK end_time > start_time, end_date ≥ start_date |
| mode | VARCHAR(20) | CHECK: ONLINE, OFFLINE, HYBRID |
| capacity, classroom, meeting_url | | CHECK: an OFFLINE batch cannot carry a meeting_url |
| status | VARCHAR(20) | CHECK: PLANNED, ONGOING, COMPLETED, CANCELLED |
| created_at, updated_at, created_by, updated_by | | |

### `batch_trainers` — A5
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| batch_id | BIGINT | **FK →** batches.id, `ON DELETE CASCADE` |
| trainer_id, trainer_user_id, trainer_name | | *cross-svc* → admission/identity |
| role | VARCHAR(20) | CHECK: PRIMARY, CO_TRAINER, GUEST; UNIQUE (batch_id, trainer_id) |

### `enrollments`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| student_id, user_id, student_code, student_name | | *cross-svc* → admission/identity, denormalised |
| course_id | BIGINT | *cross-svc* → course-service |
| batch_id | BIGINT | **FK →** batches.id |
| enrolled_at, completion_date, dropped_reason | | |
| status | VARCHAR(20) | CHECK: ACTIVE, COMPLETED, DROPPED, SUSPENDED, TRANSFERRED |
| created_at, updated_at, created_by, updated_by | | Partial unique index: one ACTIVE row per (student_id, batch_id) — enforces "not enrolled twice in the same active batch" (Doc S14) |

### `class_sessions` (Doc S6.7)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| batch_id | BIGINT | **FK →** batches.id, `ON DELETE CASCADE` |
| trainer_id, trainer_user_id | | |
| session_date, start_time, end_time, topic | | UNIQUE (batch_id, session_date, start_time); CHECK end_time > start_time |
| mode, meeting_url, room | | mode CHECK: ONLINE, OFFLINE, HYBRID |
| status | VARCHAR(20) | CHECK: SCHEDULED, COMPLETED, CANCELLED, RESCHEDULED |
| attendance_marked, attendance_auto | BOOLEAN | `attendance_auto` distinguishes a liveclass-computed row from a trainer's manual entry |
| cancelled_reason, created_at, updated_at, created_by, updated_by | | |

### `attendance` (Doc S6.9) — R8, R10
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| session_id | BIGINT | **FK →** class_sessions.id, `ON DELETE CASCADE` |
| student_id | BIGINT | *cross-svc*; UNIQUE (session_id, student_id) |
| status | VARCHAR(20) | CHECK: PRESENT, ABSENT, LATE, EXCUSED (R8 — excused is left out of the percentage entirely) |
| remark, attended_minutes | | attended_minutes only set for LIVE_CLASS-sourced rows |
| source | VARCHAR(20) | CHECK: MANUAL, LIVE_CLASS, IMPORT |
| marked_at, marked_by | | |
| corrected_at, corrected_by | | R10 — a re-mark is a correction, always audited, requires a reason (enforced in the service, not the schema) |

---

## liveclass-service — `itilms_liveclass` — A6

New work not in the source PDF (§22 lists live classes as future work only). See [D3](02-documentation-review.md#1-decisions-that-change-the-design).

### `live_sessions`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL | **PK** |
| class_session_id | BIGINT | UNIQUE, *cross-svc* → batch-service's `class_sessions.id` — one room per scheduled session |
| batch_id, batch_code, course_title | | denormalised |
| trainer_id, trainer_user_id | | |
| room_name | VARCHAR(120) | UNIQUE, derived from the session id (not random) so a room always traces back |
| livekit_room_sid, topic | | |
| session_date, start_time, end_time | | as scheduled |
| scheduled_start_at, scheduled_end_at | TIMESTAMPTZ | the same times resolved once against institute timezone; CHECK end > start |
| status | VARCHAR(20) | CHECK: SCHEDULED, LIVE, ENDED, CANCELLED |
| started_at, ended_at, max_participants, peak_participants | | |
| recording_enabled, recording_url | | |
| attendance_computed, attendance_computed_at | BOOLEAN, TIMESTAMPTZ | stops a late webhook or sweep re-run from double-publishing attendance; CHECK ties the two together |
| created_at, updated_at, created_by, updated_by | | |

### `live_participants`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL | **PK** |
| live_session_id | BIGINT | **FK →** live_sessions.id, `ON DELETE CASCADE` |
| identity | VARCHAR(80) | the LiveKit identity `user-<userId>`; UNIQUE (live_session_id, identity) |
| user_id, student_id, display_name | | student_id null for trainer/staff observers |
| role | VARCHAR(20) | CHECK: TRAINER, STUDENT, STAFF |
| first_joined_at, last_left_at, current_join_at | | `current_join_at` set while in the room; how the sweep finds an unclean disconnect |
| attended_seconds, join_count | | summed across every reconnect |
| attendance_percent, computed_status, created_at, updated_at, created_by, updated_by | | |

### `live_participant_events`
| Column | Type | Notes |
|---|---|---|
| id | BIGSERIAL | **PK** |
| live_session_id | BIGINT | **FK →** live_sessions.id, `ON DELETE CASCADE` |
| identity, user_id, event_type, participant_sid, occurred_at | | raw webhook log — lets attendance be recomputed if thresholds change or a figure is disputed |
| livekit_event_id | VARCHAR(80) | UNIQUE — LiveKit retries until it gets a 200; this is what makes a replay harmless |
| created_at | | |

---

## assessment-service — `itilms_assessment`

Assignments/submissions (Doc S6.10) and MCQ tests/attempts/results (Doc S6.11) — O3, O4.

### `assignments`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| batch_id, course_id | BIGINT | *cross-svc* |
| title, instructions, attachment_ref, due_at, max_marks | | CHECK max_marks > 0 |
| allow_late | BOOLEAN | off refuses a late submission outright instead of marking it LATE |
| mandatory | BOOLEAN | counts toward course completion (Doc S7.3) |
| status | VARCHAR(20) | CHECK: DRAFT, PUBLISHED, CLOSED; CHECK requires published_at once PUBLISHED |
| trainer_id, published_at, created_at, updated_at, created_by, updated_by | | |

### `assignment_submissions` — O4
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| assignment_id | BIGINT | **FK →** assignments.id, `ON DELETE CASCADE`; UNIQUE (assignment_id, student_id) — one row, updated on resubmit |
| student_id, student_name, student_user_id | | |
| text_answer, submitted_at, submission_count | | |
| status | VARCHAR(20) | CHECK: SUBMITTED, LATE, EVALUATED, RETURNED |
| marks, feedback, evaluated_at, evaluated_by | | CHECK: EVALUATED requires both marks and evaluated_at |
| created_at, updated_at, created_by, updated_by | | |

### `submission_files`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| submission_id | BIGINT | **FK →** assignment_submissions.id, `ON DELETE CASCADE` |
| file_ref | VARCHAR(120) | *cross-svc* → file-service; UNIQUE (submission_id, file_ref) |
| file_name, content_type, size_bytes, uploaded_at | | |

### `quizzes` — O3
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| course_id | BIGINT | *cross-svc* |
| batch_id | BIGINT | null = open to every batch on the course |
| title, instructions, duration_minutes | | CHECK duration_minutes > 0 |
| pass_percentage, attempts_allowed | | CHECK 0–100, > 0 |
| total_marks | | denormalised sum of question marks |
| available_from, available_until | | CHECK until > from |
| shuffle_questions, show_result_immediately, mandatory | BOOLEAN | |
| status | VARCHAR(20) | CHECK: DRAFT, PUBLISHED, CLOSED; CHECK requires published_at + total_marks > 0 once PUBLISHED |
| trainer_id, published_at, created_at, updated_at, created_by, updated_by | | |

### `quiz_questions`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| quiz_id | BIGINT | **FK →** quizzes.id, `ON DELETE CASCADE`; UNIQUE (quiz_id, sequence_no) |
| question_text, type | | type CHECK: SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE — options moved off the PDF's fixed option_a–d columns (O3) |
| marks, sequence_no, explanation, created_at, updated_at, created_by, updated_by | | |

### `quiz_options`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| question_id | BIGINT | **FK →** quiz_questions.id, `ON DELETE CASCADE`; UNIQUE (question_id, sequence_no) |
| option_text | | |
| is_correct | BOOLEAN | never sent to a student before submission |
| sequence_no | | |

### `quiz_attempts`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| quiz_id | BIGINT | **FK →** quizzes.id, `ON DELETE CASCADE` |
| student_id, student_name, student_user_id, batch_id | | |
| attempt_no | | UNIQUE (quiz_id, student_id, attempt_no) |
| started_at, expires_at, submitted_at | | expires_at fixed server-side at start — closing the laptop buys no extra time |
| score, percentage, passed | | CHECK: both set once not IN_PROGRESS |
| status | VARCHAR(20) | CHECK: IN_PROGRESS, SUBMITTED, EXPIRED |
| created_at, updated_at, created_by, updated_by | | |

### `quiz_answers`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| attempt_id | BIGINT | **FK →** quiz_attempts.id, `ON DELETE CASCADE` |
| question_id | BIGINT | **FK →** quiz_questions.id, `ON DELETE CASCADE`; UNIQUE (attempt_id, question_id) |
| correct, marks_awarded, answered_at | | |

### `quiz_answer_options`
| Column | Type | Notes |
|---|---|---|
| answer_id | BIGINT | **PK** (composite), **FK →** quiz_answers.id, `ON DELETE CASCADE` |
| option_id | BIGINT | **PK** (composite), **FK →** quiz_options.id, `ON DELETE CASCADE` — one row per option ticked, so a multi-choice answer is queryable |

---

## finance-service — `itilms_finance`

Fee plans, instalments, payments (Doc S6.12). Outstanding = net fee − successful payments, **computed on every read, never stored** (Doc S14) — see O1, O2.

### `fee_plans`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| student_id, student_user_id, student_code, student_name | | *cross-svc* → admission, denormalised |
| course_id, batch_id | BIGINT | *cross-svc* |
| total_fee, discount, net_fee, currency | NUMERIC(12,2) | CHECK: net_fee = total_fee − discount (pinned, cannot drift) |
| status | VARCHAR(20) | CHECK: ACTIVE, SETTLED, CANCELLED |
| notes, cancelled_at, cancelled_reason | | CHECK: CANCELLED requires cancelled_at |
| created_at, updated_at, created_by, updated_by | | Partial unique index: one non-CANCELLED plan per (student_id, course_id) |

### `fee_installments`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| fee_plan_id | BIGINT | **FK →** fee_plans.id, `ON DELETE CASCADE`; UNIQUE (fee_plan_id, installment_no) |
| installment_no, due_date, amount | | CHECK amount > 0 |
| last_reminder_days, overdue_notified_at | | tracks which reminder (7/3/1 day) already went out |

O2: no `status` column here — paid/overdue is derived at read time from `payments`, applied oldest-due-first.

### `payments`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| fee_plan_id | BIGINT | **FK →** fee_plans.id |
| student_id | | |
| amount, payment_date, method | | method CHECK: CASH, UPI, CARD, BANK_TRANSFER, CHEQUE, ONLINE |
| reference_no | | UNIQUE (method, reference_no) where SUCCESS — catches the same UPI/cheque entered twice |
| receipt_no | | UNIQUE, from `receipt_seq` (RCPT-2026-000123 style) |
| status | VARCHAR(20) | CHECK: SUCCESS, REVERSED — a bounced cheque is reversed, never deleted |
| reversed_at, reversed_by, reversal_reason | | CHECK: REVERSED requires both |
| notes, created_at, updated_at, created_by, updated_by | | |

Plus `receipt_seq` — a standalone sequence, not a table, backing `receipt_no`.

---

## certificate-service — `itilms_certificate`

Completion checks, issue, and public verification (Doc S6.13, S7.3) — O5.

### `certificates`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| certificate_no | VARCHAR(40) | UNIQUE, sequential (e.g. `ITILMS-2026-0000123`), from `certificate_seq` |
| verification_code | VARCHAR(16) | random, unguessable — O5: a sequential number alone would let anyone walk the verification page |
| student_id, student_user_id, student_code, student_name | | |
| course_id, course_title, batch_id | | |
| issue_date, completion_date | | |
| status | VARCHAR(20) | CHECK: ISSUED, REVOKED |
| revoked_at, revoked_by, revoked_reason | | CHECK: REVOKED requires both |
| evidence | TEXT | the figures each criterion was checked against at issue time, as JSON — the answer to "on what basis?" a year later |
| issued_by, created_at, updated_at, created_by, updated_by | | Partial unique index: one ISSUED certificate per (student_id, course_id) |

Plus `certificate_seq`.

---

## placement-service — `itilms_placement`

Companies, jobs, applications, interview pipeline (Doc S6.14, S7.4).

### `companies`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| name | | UNIQUE on `lower(trim(name))` — "Infosys" and "infosys " must not split placement history |
| industry, website, location, contact_name, contact_email, contact_phone, notes, active | | |
| created_at, updated_at, created_by, updated_by | | |

### `job_openings`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| company_id | BIGINT | **FK →** companies.id |
| title, description | | |
| job_type | VARCHAR(20) | CHECK: FULL_TIME, INTERNSHIP, CONTRACT, PART_TIME |
| location, package_offered, openings, application_deadline | | |
| require_certificate, min_attendance_percent, min_score_percent, eligibility_notes | | enforced when a student applies, not merely displayed (Doc S7.4) |
| status | VARCHAR(20) | CHECK: DRAFT, OPEN, CLOSED, CANCELLED |
| published_at, created_at, updated_at, created_by, updated_by | | |

### `job_eligible_courses`
| Column | Type | Notes |
|---|---|---|
| job_id | BIGINT | **PK** (composite), **FK →** job_openings.id, `ON DELETE CASCADE` |
| course_id | BIGINT | **PK** (composite), *cross-svc*. Empty set = any course qualifies |

### `job_applications`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| job_id | BIGINT | **FK →** job_openings.id; UNIQUE (job_id, student_id) |
| student_id, student_user_id, student_name, course_id | | course_id recorded at application time |
| resume_ref | | *cross-svc* → file-service (`RESUME` category) |
| cover_note, applied_at | | |
| stage | VARCHAR(20) | CHECK: APPLIED, SHORTLISTED, INTERVIEW, ON_HOLD, SELECTED, REJECTED, WITHDRAWN |
| current_round, next_interview_at, offer_details, decided_at | | |
| created_at, updated_at, created_by, updated_by | | |

### `application_stage_history` (Doc S14)
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| application_id | BIGINT | **FK →** job_applications.id, `ON DELETE CASCADE` |
| from_stage, to_stage, round_no, note, changed_by, changed_at | | one row per pipeline move, written in the same transaction as the move itself |

---

## notification-service — `itilms_notification` — A8

In-app notifications, announcements, email delivery (Doc S16). `recipients` and `batch_members` are **read-only projections**, not sources of truth, kept so "everyone in batch 12" can be expanded without calling other services.

### `recipients`
| Column | Type | Notes |
|---|---|---|
| user_id | BIGINT | **PK** |
| email, full_name, role, active, status_changed_at, updated_at | | fed by identity-service events |

### `batch_members`
| Column | Type | Notes |
|---|---|---|
| batch_id | BIGINT | **PK** (composite) |
| user_id | BIGINT | **PK** (composite) |
| course_id, active, changed_at | | former members kept with `active = false` so a course announcement still reaches alumni |

### `notifications`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| source_event_id, user_id | | UNIQUE (source_event_id, user_id) — a redelivered Kafka event becomes a no-op, not a duplicate |
| type, title, message, action_url, read_at, created_at | | |

### `announcements`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| title, message | | |
| audience | VARCHAR(10) | CHECK: ALL, ROLE, BATCH, COURSE |
| target_role, target_id | | CHECK pairs audience with the right target column |
| send_email, expires_at, withdrawn, recipient_count | | |
| created_at, updated_at, created_by, updated_by | | |

### `email_outbox`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| user_id, to_address, subject, text_body, html_body | | secrets (temp passwords, reset tokens) are written straight into the body here and never into `notifications` |
| status | VARCHAR(10) | CHECK: PENDING, SENT, FAILED |
| attempts, next_attempt_at, last_error, sent_at, created_at | | doubling backoff via `EmailOutboxJob` |

---

## file-service — `itilms_file` — A9

The bytes live on disk or in MinIO; this is the only record of what was stored and who may read it back.

### `files`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| storage_key | VARCHAR(300) | UNIQUE, never handed to a client — downloads go through `/api/files/{id}/download` |
| original_filename, content_type, size_bytes | | |
| category | VARCHAR(30) | CHECK: AVATAR, DOCUMENT, ASSIGNMENT, SUBMISSION, LESSON_RESOURCE, CERTIFICATE, RECEIPT, RESUME — access rules (`FileAccessRules`) key off this, not a live batch/enrolment check (L1/L2-style simplification) |
| owner_user_id, uploaded_by | | owner is not always the uploader — staff can add a document on a student's behalf |
| created_at, updated_at, created_by, updated_by | | |

---

## reporting-service — `itilms_reporting` — A10

Three read models, all fed by Kafka events; nothing here is written directly by a person.

### `audit_logs`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| event_id | VARCHAR(64) | UNIQUE — de-duplicates a redelivered `AuditRecordedEvent` |
| occurred_at, service_name, actor_user_id, actor_email, actor_role, action, entity_type, entity_id, old_value, new_value, ip_address, user_agent, recorded_at | | one row per audited action, from every service |

### `activity_events`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| event_id, metric_key | | UNIQUE (event_id, metric_key) |
| occurred_at, event_date, amount, ref_id, dimension | | one row per dashboard-relevant fact, grouped by `metric_key` **at read time** rather than kept as a running counter — a redelivered event cannot inflate a total |

### `student_session_attendance`
| Column | Type | Notes |
|---|---|---|
| id | BIGINT | **PK** |
| student_id, session_id | | UNIQUE (student_id, session_id) — keyed so a correction upserts the same row instead of double-counting |
| batch_id, session_date | | |
| status | VARCHAR(20) | CHECK: PRESENT, ABSENT, LATE, EXCUSED |
| marked_at | | mirrors `AttendanceMarkedEvent` from batch-service |

---

## Summary

| Service | Database | Tables |
|---|---|---|
| identity | itilms_identity | 3 |
| admission | itilms_admission | 6 |
| course | itilms_course | 5 |
| batch | itilms_batch | 5 |
| liveclass | itilms_liveclass | 3 |
| assessment | itilms_assessment | 9 |
| finance | itilms_finance | 3 |
| certificate | itilms_certificate | 1 |
| placement | itilms_placement | 5 |
| notification | itilms_notification | 5 |
| file | itilms_file | 1 |
| reporting | itilms_reporting | 3 |

35 tables across 12 databases. See [01-architecture.md](01-architecture.md) for the Kafka events that keep the denormalised copies (course titles in `batches`, `course_enrollments`, `recipients`, `batch_members`, ...) current, and [02-documentation-review.md](02-documentation-review.md) for why each one exists.
