# API Reference

Every request goes through the gateway. There is no other way in — service ports (8081–8091) are not published, even in Docker (see [01-architecture.md](01-architecture.md)).

| | |
|---|---|
| Base URL (local) | `http://localhost:8080` |
| Interactive docs | `http://localhost:8080/swagger-ui.html` — every service on one page, springdoc-generated from the same `@Operation`/`@PreAuthorize` annotations this file was built from |
| Auth | `Authorization: Bearer <access token>` on every endpoint except the public ones marked **public** below |
| Getting a token | `POST /api/auth/login` |
| Content type | `application/json` throughout |

This file lists every controller endpoint, grouped by service, with the role it requires and a one-line description. It does not repeat request/response bodies — read those from Swagger UI (generated, always current) or the DTOs under each service's `dto/request` and `dto/response` packages. Paths are as declared; `{x}` is a path variable.

A handful of endpoints are marked **internal only, 404 via gateway** rather than a role — the gateway refuses any external request to an `/internal/` path outright, before authentication is even checked, so no bearer token gets one through. They exist solely for one service to call another over the container network (see the note at the end of this file).

Regenerate the endpoint/role columns at any time with:
```
node deploy/scripts/check-gateway-routes.mjs   # also confirms the gateway routes match (181 endpoints, 0 misrouted)
```

---

## Error contract

Every service returns the same shape on failure (`common-lib`'s `ErrorResponse`):

```json
{
  "timestamp": "2026-09-23T10:15:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "code": "NOT_ELIGIBLE",
  "message": "Attendance is below the job's minimum of 75%",
  "path": "/api/jobs/12/apply",
  "fieldErrors": null,
  "details": null
}
```

- `code` is stable and machine-readable — the frontend branches on it, not on `message`.
- `fieldErrors` is populated only for `400 VALIDATION_FAILED` (a map of field name → message).
- `message` is safe to show a user as-is.

## Roles

`ADMIN`, `COORDINATOR`, `TRAINER`, `STUDENT`, `PLACEMENT`, `FINANCE` — one role per user (see [R1/R2](02-documentation-review.md), no separate `roles` table). Common shorthand used below, taken from `common-lib`'s `Roles` constants:

| Shorthand | Expands to |
|---|---|
| **STAFF** | ADMIN, COORDINATOR |
| **ACADEMIC** | ADMIN, COORDINATOR, TRAINER |
| **ADMIN_ONLY** | ADMIN |
| **FINANCE_DESK** | ADMIN, FINANCE |
| **FINANCE_VIEW** | ADMIN, FINANCE, COORDINATOR |
| **PLACEMENT_DESK** | ADMIN, PLACEMENT |
| **any signed-in user** | `isAuthenticated()` — any valid token, any role |
| **public** | no token required — the gateway still rate-limits `/api/auth/**` (5 req/s, burst 10) |

Endpoints not shown as public or role-restricted below still require a valid token; where a table doesn't repeat a role because a controller class carries one `@PreAuthorize` for all its methods (e.g. notification-service), that is noted once at the top of the section instead of on every row.

---

## identity-service

### `AuthController` — `/api/auth`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/auth/login | public | Sign in with email or phone; returns access + refresh token |
| POST | /api/auth/register | public | Self-registration — **403 unless `itilms.security.self-registration-enabled` is set** (R5, off by default) |
| POST | /api/auth/refresh | public | Rotates the refresh token; reusing an already-rotated token revokes the whole session family |
| POST | /api/auth/logout | public | Revokes one refresh token |
| POST | /api/auth/logout-all | any signed-in user | Revokes every session |
| POST | /api/auth/change-password | any signed-in user | Also revokes every other session |
| POST | /api/auth/forgot-password | public | Issues a single-use reset token, emailed only (never returned in the response or stored anywhere but its hash) |
| POST | /api/auth/reset-password | public | Consumes the reset token |
| GET | /api/auth/me | any signed-in user | Who the token belongs to |

### `UserController` — `/api/users`, `InternalUserController` — `/api/users/internal`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/users | STAFF | List users |
| GET | /api/users/{id} | STAFF | Get one user |
| POST | /api/users | ADMIN_ONLY | Create a user account (also used to create trainer/finance/placement accounts) |
| PUT | /api/users/{id} | ADMIN_ONLY | Update details |
| PATCH | /api/users/{id}/status | ADMIN_ONLY | Activate, deactivate or block |
| POST | /api/users/{id}/reset-password | ADMIN_ONLY | Admin-initiated reset |
| GET | /api/users/stats/counts | STAFF | Active user counts by role |
| POST | /api/users/internal/lookup | **internal only, 404 via gateway** | *cross-service:* resolve many user ids at once |
| GET | /api/users/internal/by-role | **internal only, 404 via gateway** | *cross-service:* every active user holding a role |

---

## admission-service

### `LeadController` — `/api/leads`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/leads/enquiry | public | Website enquiry capture |
| GET | /api/leads | ADMIN, COORDINATOR, PLACEMENT | List leads |
| GET | /api/leads/my-overdue | ADMIN, COORDINATOR, PLACEMENT | The caller's own overdue follow-ups |
| GET | /api/leads/overdue | STAFF | Every overdue follow-up, all counsellors (R3) |
| GET | /api/leads/stats/funnel | ADMIN, COORDINATOR, PLACEMENT | Conversion funnel |
| GET | /api/leads/{id} | ADMIN, COORDINATOR, PLACEMENT | Get a lead |
| POST | /api/leads | ADMIN, COORDINATOR, PLACEMENT | Create a lead |
| PUT | /api/leads/{id} | ADMIN, COORDINATOR, PLACEMENT | Update a lead |
| POST | /api/leads/{id}/followups | ADMIN, COORDINATOR, PLACEMENT | Log a follow-up (A2) |
| GET | /api/leads/{id}/followups | ADMIN, COORDINATOR, PLACEMENT | Follow-up history |
| POST | /api/leads/{id}/convert | ADMIN, COORDINATOR, PLACEMENT | Admit the lead — creates the student profile and, via Feign, the identity account |

### `StudentController` — `/api/students`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/students | ADMIN, COORDINATOR, PLACEMENT, FINANCE | List students |
| GET | /api/students/me | STUDENT | My own profile |
| PUT | /api/students/me | STUDENT | Update my own profile |
| GET | /api/students/{id} | ADMIN, COORDINATOR, PLACEMENT, FINANCE, TRAINER | Get a student — **L2:** any trainer, not just one teaching this student |
| POST | /api/students | STAFF | Admit a student directly (bypassing the lead flow) |
| PUT | /api/students/{id} | STAFF | Update a profile |
| PATCH | /api/students/{id}/status | STAFF | Change standing (ACTIVE/ALUMNI/DROPPED/SUSPENDED) |
| GET | /api/students/stats/counts | ADMIN, COORDINATOR, FINANCE, PLACEMENT | Counts by standing |
| POST | /api/students/internal/lookup | **internal only, 404 via gateway** | *cross-service:* resolve many student ids |

### `TrainerController` — `/api/trainers`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/trainers | ACADEMIC | List trainers |
| GET | /api/trainers/me | TRAINER | My own trainer profile |
| GET | /api/trainers/{id} | ACADEMIC | Get a trainer |
| POST | /api/trainers | STAFF | Create a trainer (+ identity account) |
| PUT | /api/trainers/{id} | STAFF | Update |
| GET | /api/trainers/available | STAFF | Trainers qualified for a given course (A3) |
| POST | /api/trainers/internal/lookup | **internal only, 404 via gateway** | *cross-service:* resolve many trainer ids |

---

## course-service

### `CourseController` — `/api/courses`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/courses/public | public | Browse the published catalog |
| GET | /api/courses/public/{id} | public | Public course page |
| GET | /api/courses | any signed-in user | List courses (incl. drafts, for staff) |
| GET | /api/courses/{id} | any signed-in user | Course with curriculum — **honours enrolment/preview rules** (`EnrollmentStatus.allowsContentAccess()`) |
| POST | /api/courses | STAFF | Create a course (draft) |
| PUT | /api/courses/{id} | STAFF | Update |
| POST | /api/courses/{id}/publish | STAFF | Publish — refuses if required metadata is missing, naming each field |
| POST | /api/courses/{id}/archive | ADMIN_ONLY | Archive |
| GET | /api/courses/stats/counts | STAFF | Counts by status |
| POST | /api/courses/internal/lookup | **internal only, 404 via gateway** | *cross-service:* resolve many course ids |

### `CurriculumController` (modules and lessons, no own base path)
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/courses/{courseId}/modules | any signed-in user | List modules |
| POST | /api/courses/{courseId}/modules | ACADEMIC | Add a module |
| PUT | /api/modules/{moduleId} | ACADEMIC | Update |
| DELETE | /api/modules/{moduleId} | STAFF | Delete |
| PUT | /api/courses/{courseId}/modules/order | ACADEMIC | Reorder modules |
| GET | /api/modules/{moduleId}/lessons | ACADEMIC | List lessons |
| POST | /api/modules/{moduleId}/lessons | ACADEMIC | Add a lesson (optional `codeLanguage` + `starterCode` give it a practice editor) |
| PUT | /api/lessons/{lessonId} | ACADEMIC | Update |
| DELETE | /api/lessons/{lessonId} | ACADEMIC | Delete |
| PUT | /api/modules/{moduleId}/lessons/order | ACADEMIC | Reorder lessons |

*Course-level actions (create, update, publish, stats) are STAFF-only; archiving is ADMIN-only. Curriculum edits (modules, lessons) are open to TRAINER too (ACADEMIC) — except deleting a module, which is STAFF-only.*

### `ProgressController` — `/api/progress`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/progress/lessons/{lessonId} | STUDENT | Record progress on a lesson (watched seconds / completed) |
| GET | /api/progress/me | STUDENT | My own progress |
| GET | /api/progress/internal/students/{studentId} | **internal only, 404 via gateway** | *cross-service:* every course a student is taking, module by module, with video lessons finished (for the progress report) |
| GET | /api/progress/batches/{batchId} | ACADEMIC | Progress for a whole batch — **L1:** any trainer, not just one teaching this batch |
| GET | /api/progress/students/{studentId}/courses/{courseId} | any signed-in user | Progress for one student on one course — ownership-checked (a student may only read their own; fixed defect, see [02-documentation-review.md §7](02-documentation-review.md#7-defects-found-and-fixed-during-this-review)) |

---

## batch-service

### `BatchController` — `/api/batches`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/batches | any signed-in user | List batches |
| GET | /api/batches/mine | STUDENT, TRAINER | My own batches |
| GET | /api/batches/{id} | any signed-in user | Get a batch |
| POST | /api/batches | STAFF | Create — checks the course exists via Feign |
| PUT | /api/batches/{id} | STAFF | Update |
| POST | /api/batches/{id}/students | STAFF | Enrol students — partial unique index blocks a double-active-enrolment |
| GET | /api/batches/{id}/students | ACADEMIC | Roster |
| POST | /api/batches/enrollments/{enrollmentId}/drop | STAFF | Drop a student |
| POST | /api/batches/enrollments/{enrollmentId}/transfer | STAFF | Transfer to another batch |
| GET | /api/batches/stats/counts | ACADEMIC | Counts by status |
| POST | /api/batches/internal/lookup | **internal only, 404 via gateway** | *cross-service:* resolve many batch ids |
| GET | /api/batches/internal/{batchId}/enrolled/{studentId} | **internal only, 404 via gateway** | *cross-service:* is this student in this batch? (used by liveclass, assessment, placement, certificate) |

### `CourseRequestController` — `/api/course-requests`
A student asks to join a course; an administrator or coordinator decides. Approving enrols through the normal enrolment path, so batch status, capacity and the double-enrolment rule apply.

| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/course-requests | STUDENT | Ask to join a published course (optional preferred batch, message). One open request per course; refused if already enrolled |
| GET | /api/course-requests/mine | STUDENT | My requests, newest first |
| POST | /api/course-requests/{id}/cancel | STUDENT | Withdraw my own waiting request |
| GET | /api/course-requests | STAFF | Queue, filter `?status=PENDING/APPROVED/REJECTED/CANCELLED` |
| GET | /api/course-requests/{id} | any signed-in user | One request — the student who made it, or staff; anyone else gets 404 |
| POST | /api/course-requests/{id}/approve | STAFF | Enrol the student in a batch of the course (`batchId`, or the one they asked for) |
| POST | /api/course-requests/{id}/reject | STAFF | Reject; a reason is required and the student sees it |

### `AttendanceController` (no own base path)
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/sessions/{sessionId}/attendance | ACADEMIC | Save a register — **a re-mark of an already-marked session requires a `reason` field** (R10, audited) |
| GET | /api/sessions/{sessionId}/attendance | any signed-in user | A session's register |
| GET | /api/students/me/attendance | STUDENT | My own attendance rows |
| GET | /api/students/me/attendance/summary | STUDENT | My own attendance percentage |
| GET | /api/attendance/students/{studentId} | any signed-in user | A student's percentage — ownership/role-checked |
| GET | /api/attendance/batches/{batchId} | ACADEMIC | Attendance across a batch |
| GET | /api/attendance/batches/{batchId}/alerts | ACADEMIC | Students below the attendance threshold |

### `ScheduleController` (no own base path)
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/timetable | any signed-in user | My own timetable (role-aware: student/trainer/staff) |
| GET | /api/timetable/today | any signed-in user | Today's classes |
| GET | /api/timetable/pending-attendance | ACADEMIC | Sessions still awaiting a register |
| GET | /api/batches/{batchId}/sessions | any signed-in user | A batch's full schedule |
| GET | /api/sessions/{id} | any signed-in user | One session |
| POST | /api/sessions | ACADEMIC | Schedule a session — provisions a LiveKit room if the batch is online/hybrid |
| PUT | /api/sessions/{id} | ACADEMIC | Update / reschedule |
| POST | /api/sessions/{id}/cancel | ACADEMIC | Cancel |
| POST | /api/batches/{batchId}/sessions/generate | STAFF | Generate the full timetable from a weekly pattern |

---

## liveclass-service

### `LiveClassController` — `/api/liveclass`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/liveclass/class-sessions/{classSessionId}/join | any signed-in user | Join — checks batch-service that the caller belongs, mints a signed LiveKit token (student token ≠ editable into a trainer token) |
| GET | /api/liveclass/class-sessions/{classSessionId} | any signed-in user | Room status for a timetable session |
| GET | /api/liveclass/upcoming | any signed-in user | My upcoming live classes |
| GET | /api/liveclass/me/attendance | STUDENT | My own time-in-room history |
| GET | /api/liveclass/sessions/{id} | ACADEMIC | A live class with its participant list |
| GET | /api/liveclass/batches/{batchId} | ACADEMIC | A batch's live classes |
| POST | /api/liveclass/sessions/{id}/end | ACADEMIC | End the class early |
| DELETE | /api/liveclass/sessions/{id}/participants/{userId} | ACADEMIC | Remove a participant |
| GET | /api/liveclass/sessions/{id}/controls | ACADEMIC, host of the class | Room policy and what each participant may switch on |
| PUT | /api/liveclass/sessions/{id}/policy | ACADEMIC, host | What students may switch on (mic, camera, screen share); applied at once to those in the room. Defaults: mic and camera on, screen share off |
| PUT | /api/liveclass/sessions/{id}/participants/{userId}/permissions | ACADEMIC, host | Allow/deny one student's mic, camera or screen; `followRoom` clears the override. Trainers/staff cannot be restricted |
| POST | /api/liveclass/sessions/{id}/participants/{userId}/mute | ACADEMIC, host | Switch off one student's MICROPHONE, CAMERA or SCREEN_SHARE |
| POST | /api/liveclass/sessions/{id}/mute-all | ACADEMIC, host | Mute every student microphone (hosts left alone) |

### Webhook — `/api/liveclass/webhook`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/liveclass/webhook | public\* | LiveKit event delivery — \*not actually open: verified by LiveKit's own HMAC signature, not a user JWT; an unsigned or wrongly-signed call gets 401 |

---

## assessment-service

### `AssignmentController` — `/api/assignments`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/assignments | ACADEMIC | Set an assignment (draft) |
| PUT | /api/assignments/{id} | ACADEMIC | Edit |
| POST | /api/assignments/{id}/publish | ACADEMIC | Publish |
| POST | /api/assignments/{id}/close | ACADEMIC | Stop accepting submissions |
| GET | /api/assignments/{id} | any signed-in user | One assignment |
| GET | /api/assignments/batches/{batchId} | ACADEMIC | A batch's assignments |
| GET | /api/assignments/mine | STUDENT | My assignments |
| POST | /api/assignments/{id}/submissions | STUDENT | Hand work in (text + file-service attachments); resubmission allowed until the deadline |
| GET | /api/assignments/{id}/submissions | ACADEMIC | Every submission — trainer access checked against batch-service |
| GET | /api/assignments/batches/{batchId}/awaiting-evaluation | ACADEMIC | Marking queue |

### `SubmissionController` — `/api/submissions`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/submissions/{id} | any signed-in user | One submission — owner or the batch's trainer/staff only |
| PUT | /api/submissions/{id}/evaluate | ACADEMIC | Mark, or return for rework |

### `QuizController` — `/api/quizzes`
| Method | Path | Role | Does |
|---|---|---|---|
| PUT | /api/quizzes/{id} | ACADEMIC | Change a draft's settings |
| GET | /api/quizzes/{id} | ACADEMIC | A test **with its answer key** — never served to a student |
| GET | /api/quizzes | ACADEMIC | List tests |
| POST | /api/quizzes | ACADEMIC | Create a test (draft). `secureMode` (default false) turns on the secure-test rules; `maxViolations` (1-10, default 2: one warning, then termination) is the counted violation that ends an attempt. `requireCamera` (default false) makes students allow their camera before starting and be watched for a visible face; independent of `secureMode` |
| POST | /api/quizzes/{id}/questions | ACADEMIC | Add a question. `type` is SINGLE_CHOICE, MULTI_CHOICE, TRUE_FALSE (options), SHORT_ANSWER (`acceptedAnswers`, matched ignoring case and extra spaces) or CODING (`codeLanguage`, `starterCode`, 1-10 `testCases` with input, expected output, weight, hidden). A field that does not belong to the type is refused |
| PUT | /api/quizzes/questions/{questionId} | ACADEMIC | Edit a question |
| DELETE | /api/quizzes/questions/{questionId} | ACADEMIC | Remove a question |
| POST | /api/quizzes/{id}/publish | ACADEMIC | Publish — refuses at zero total marks |
| POST | /api/quizzes/{id}/close | ACADEMIC | Close |
| GET | /api/quizzes/{id}/results | ACADEMIC | Every result |
| GET | /api/quizzes/available | STUDENT | Tests the caller can currently take |
| POST | /api/quizzes/{id}/attempts | STUDENT | Start (or resume) an attempt |
| GET | /api/quizzes/{id}/attempts/mine | STUDENT | My own attempts |

### `AttemptController` — `/api/quiz-attempts`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/quiz-attempts/{id} | STUDENT | The paper for a running attempt — **no `correct` field anywhere in the payload** |
| PUT | /api/quiz-attempts/{id}/answers | STUDENT | Autosave answers so far — chosen options, or `answerText` for a short answer or code |
| POST | /api/quiz-attempts/{id}/questions/{questionId}/run-tests | STUDENT | Run a coding question's test cases against the code and keep it as the answer. Hidden cases return pass or fail only. 429 when running too often; the code is kept either way |
| POST | /api/quiz-attempts/{id}/violations | STUDENT | Secure and camera tests: the browser reports what it noticed. Camera events (`FACE_NOT_DETECTED`, `MULTIPLE_FACES`, `CAMERA_DISABLED`, `CAMERA_PERMISSION_DENIED`) apply to tests with `requireCamera`: each is recorded and answered with a warning message, none counts and none ends the attempt. Window events, on secure tests: the browser reports leaving the window, copying, etc. The server decides: `TAB_SWITCH` / `WINDOW_BLUR` count (a second report within 3 s of the last is merged), everything else is only recorded. A counted violation below the test's `maxViolations` warns; the one that reaches it ends the attempt (`TERMINATED`, failed). On a test that is not secure, or an attempt already over, accepted and ignored |
| GET | /api/quiz-attempts/{id}/violations | ACADEMIC | Everything the browser reported for an attempt, oldest first, with server time, browser time and whether it counted (trainers of the test, staff) |
| POST | /api/quiz-attempts/{id}/submit | STUDENT | Submit — scored server-side; re-runs the tests for code changed since its last run; resubmitting a finished attempt is a harmless repeat, same result |
| GET | /api/quiz-attempts/{id}/result | any signed-in user | An attempt's result |

### `ResultController` — `/api/results`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/results/me | STUDENT | My own results |
| GET | /api/results/students/{studentId}/completion | any signed-in user | Has this student done the mandatory assessed work their course requires? (feeds certificate eligibility) |

---

## finance-service

### `FeePlanController` — `/api/fee-plans`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/fee-plans | FINANCE_DESK | Raise a fee plan (also raised automatically from `student-admitted`) |
| GET | /api/fee-plans | FINANCE_VIEW | List fee plans |
| GET | /api/fee-plans/{id} | ADMIN, FINANCE, COORDINATOR, STUDENT | One plan — student sees only their own |
| PUT | /api/fee-plans/{id} | FINANCE_DESK | Change a plan |
| POST | /api/fee-plans/{id}/cancel | FINANCE_DESK | Cancel |

### `PaymentController` — `/api/payments`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/payments | FINANCE_DESK | Record a payment — allocated to instalments oldest-due-first (O1) |
| GET | /api/payments | FINANCE_VIEW | Payments in a date range |
| GET | /api/payments/{id} | ADMIN, FINANCE, COORDINATOR, STUDENT | One payment |
| GET | /api/payments/{id}/receipt | ADMIN, FINANCE, COORDINATOR, STUDENT | The receipt |
| POST | /api/payments/{id}/reverse | FINANCE_DESK | Reverse (e.g. bounced cheque) — kept, not deleted |

### `FeeController` — `/api/fees`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/fees/me | STUDENT | My own fees, outstanding computed live |
| GET | /api/fees/students/{studentId} | ADMIN, FINANCE, COORDINATOR, STUDENT | A student's fees |
| GET | /api/fees/dashboard | FINANCE_VIEW | Finance dashboard |
| GET | /api/fees/overdue | FINANCE_VIEW | Overdue instalments |

---

## certificate-service

### `CertificateController` — `/api/certificates`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/certificates/eligibility | ADMIN, COORDINATOR, STUDENT | Is this student eligible? Checks every criterion live against the owning service |
| POST | /api/certificates | STAFF | Issue — re-validates every criterion server-side even if `/eligibility` said yes earlier |
| POST | /api/certificates/claim | STUDENT | Student-initiated claim, same validation path |
| GET | /api/certificates/me | STUDENT | My own certificates |
| GET | /api/certificates | STAFF | List certificates |
| GET | /api/certificates/{id} | ADMIN, COORDINATOR, STUDENT | One certificate |
| GET | /api/certificates/{id}/pdf | ADMIN, COORDINATOR, STUDENT | Download as PDF |
| POST | /api/certificates/{id}/revoke | ADMIN_ONLY | Revoke |
| GET | /api/certificates/verify/{certificateNo} | public | Public verification — **requires `?code=<verificationCode>`**, rate-limited, wrong code and unknown number look identical (O5) |

---

## placement-service

### `JobController` — `/api/jobs`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/jobs | ADMIN, PLACEMENT, COORDINATOR, STUDENT | List openings — a student sees whether they may apply and why not |
| GET | /api/jobs/{id} | ADMIN, PLACEMENT, COORDINATOR, STUDENT | One opening |
| POST | /api/jobs | PLACEMENT_DESK | Create (draft) |
| PUT | /api/jobs/{id} | PLACEMENT_DESK | Edit — eligibility may be relaxed, not tightened, once students have applied |
| POST | /api/jobs/{id}/publish | PLACEMENT_DESK | Publish, notifies eligible students |
| POST | /api/jobs/{id}/close | PLACEMENT_DESK | Close applications |
| POST | /api/jobs/{id}/cancel | PLACEMENT_DESK | Cancel |
| POST | /api/jobs/{id}/apply | STUDENT | Apply — eligibility enforced server-side, not just displayed |
| GET | /api/jobs/{id}/applications | ADMIN, PLACEMENT, COORDINATOR | The recruiter's pipeline view |

---

## notification-service

*Both controllers below carry a class-level `@PreAuthorize("isAuthenticated()")` — every endpoint needs a valid token; there is no per-role split within notification-service itself (audience targeting for announcements is done by whoever calls `POST /api/announcements`, using the ACADEMIC-equivalent check on that one write).*

### `NotificationController` — `/api/notifications`
| Method | Path | Does |
|---|---|---|
| GET | /api/notifications | My notifications |
| GET | /api/notifications/unread-count | How many are unread |
| PUT | /api/notifications/{id}/read | Mark one read |
| PUT | /api/notifications/read-all | Mark all read |

### `AnnouncementController` — `/api/announcements`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/announcements | any signed-in user | Announcements for me |
| GET | /api/announcements/{id} | any signed-in user | One announcement |
| POST | /api/announcements | ACADEMIC | Make one — audience ALL/ROLE/BATCH/COURSE, and a `category` (GENERAL, COURSE, BATCH, LIVE_CLASS, TEST, ASSIGNMENT, INSTITUTE; default GENERAL). Students cannot publish |
| PUT | /api/announcements/{id} | ACADEMIC | Correct |
| POST | /api/announcements/{id}/withdraw | ACADEMIC | Withdraw |

---

## file-service

### `FileController` — `/api/files`
| Method | Path | Role | Does |
|---|---|---|---|
| POST | /api/files | any signed-in user | Upload — validated against `config-repo/file-service.yml`'s per-category MIME/size allow-list |
| GET | /api/files/mine | any signed-in user | Files I uploaded (paged) |
| GET | /api/files/{id} | any signed-in user | Metadata — access per `FileAccessRules` (category-based, see [03-erd.md](03-erd.md), file-service section) |
| GET | /api/files/{id}/download | any signed-in user | Download |
| DELETE | /api/files/{id} | any signed-in user | Delete — owner or staff |
| GET | /api/files | STAFF | List (admin directory view) |

---

## reporting-service

### `DashboardController` — `/api/dashboard`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/dashboard/summary | STAFF | Institute-wide summary, cached in memory for `dashboard-cache-seconds` |

### `StudentProgressController` — `/api/reports`
A student's complete record and progress report, put together from the services that own each part. See `10-student-progress-report.md`.

| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/reports/me/progress | STUDENT | My report: profile, batches, attendance, live classes, recorded lessons, tests, coding, assignments, seven headline percentages, and suggestions. Test results a trainer has not released are held back |
| GET | /api/reports/students/{studentId}/progress | ACADEMIC | Any student's report for ADMIN / COORDINATOR; for a TRAINER only if the student is enrolled in a batch they teach (else 403). A student uses `/me/progress`, so this is 403 for them |

### `AuditLogController` — `/api/audit-logs`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/audit-logs | ADMIN_ONLY | Search the audit trail |
| GET | /api/audit-logs/{id} | ADMIN_ONLY | One entry |

### `ReportController` — `/api/reports`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/reports/audit-logs/export | ADMIN_ONLY | CSV / Excel / PDF export, capped at `max-export-rows` |

---

## codeexec-service

Stateless - no database. Runs practice-editor code in a Judge0 sandbox. Set-up and limits: [08-code-execution.md](08-code-execution.md).

### `CodeExecController` — `/api/code`
| Method | Path | Role | Does |
|---|---|---|---|
| GET | /api/code/languages | any signed-in user | Languages switched on in configuration |
| POST | /api/code/run | any signed-in user | Run code; a compile error or crash is a `200` with an `outcome` |
| POST | /api/code/run-batch | any signed-in user | One program against several inputs (for grading): one rate-limit unit however many inputs, at most `limits.max-batch-cases` (10) |
| GET | /api/code/status | ADMIN_ONLY | Judge0 configured / reachable, and which language ids it does not know |

---

## Internal endpoints

Every `/internal/**` path (e.g. `/api/students/internal/lookup`, `/api/batches/internal/{batchId}/enrolled/{studentId}`) exists **only for service-to-service Feign calls**. The gateway answers `404` for any request to an `/internal/` path from outside the cluster — this was defect #3 in the [documentation review](02-documentation-review.md#7-defects-found-and-fixed-during-this-review) and is now enforced at the gateway itself, so no individual service's authorization has to be trusted to keep these private.
