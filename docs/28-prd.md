# Product requirements (PRD)

What IT-ILMS must do, for whom, and how we know it does. This PRD is reconstructed from three sources: the original specification (`IT_Institute_LMS_Complete_Project_Documentation.pdf`, reviewed in [02-documentation-review.md](02-documentation-review.md)), the two feature requests the owner added in September 2026, and the system as built. Where the PDF and the build disagree, the build and the recorded decision win. Requirement ids (R-xx) are stable so tests and docs can point at them.

---

## 1. Purpose

One platform for an IT training institute that carries a student from enquiry to placement, and lets the institute run classes, tests, fees and certificates without spreadsheets.

Success looks like:

- Staff admit a student, place them in a batch and raise a fee plan in one step.
- A trainer runs a live class, marks attendance (automatic and manual), sets a test, and sees who is struggling.
- A student learns, takes tests, pays fees, receives a verifiable certificate, and applies for jobs, from one login.
- The institute can trust test results and attendance.

## 2. Users

| Role | Needs |
|---|---|
| ADMIN | Everything: users, branding, audit, settings, finance, certificates |
| COORDINATOR ("manager") | Admissions, batches, courses, announcements, student requests |
| TRAINER | Their batches: classes, attendance, tests, assignments, question bank |
| STUDENT | Own courses, tests, classes, fees, certificates, progress, jobs |
| PLACEMENT | Companies, job openings, applications, interview pipeline |
| FINANCE | Fee plans, payments, receipts, overdue follow-up |

## 3. Scope and requirements

### Admissions and students
- R-01 Capture enquiries (public form) and leads with follow-ups.
- R-02 Convert a lead to a student with course, batch and fee plan in one step; a login is created and emailed.
- R-03 A newly registered student sees only the course list until staff place them in a batch; they can request a course (fee shown first) and staff approve it.
- R-04 Self-registration can be switched on or off by the admin (off by default).

### Academics
- R-10 Course catalog with modules and lessons (video, PDF, notes, link); publish only with required metadata.
- R-11 Batches with trainer, timetable, capacity, mode; no double enrolment in the same active batch.
- R-12 Lessons unlock only through an enrolment that allows content (ACTIVE or COMPLETED).
- R-13 Attendance, manual per session with an audited correction (reason required) and automatic from time in the live room.
- R-14 Practice code editor inside a lesson, run in a sandbox, in several languages, with suggestions like an IDE.
- R-15 Opt-in video-lesson monitoring: camera check, auto-pause when the face is missing, completion by watching 90%.

### Live classes
- R-20 Built-in live room (LiveKit), or per session an embedded Jitsi, Google Meet or Zoom link.
- R-21 Host controls: who may use mic, camera and screen share, per room and per person; mute all.
- R-22 Live questions (choice, short answer, coding) with timestamps, answers graded where possible, reviewable later.
- R-23 Recording and content protection (watermark, blackout when the tab is hidden); subtitles and transcript; End class button.
- R-24 Optional face-visibility monitoring, ON/OFF at institute, course, batch and class level (most specific wins), reviewable by admin and trainer.

### Assessment
- R-30 Assignments with file submissions, late marking, rework.
- R-31 Tests with MCQ, multiple select, true/false, short answer and coding questions with test cases; server-side scoring.
- R-32 Question pool per attempt, shuffled questions and options, timed, autosaved, resumable, extra attempt for one student.
- R-33 Secure test mode (full screen, tab-switch rules, copy block), camera and face checks, remote-control tool warning, typing replay.
- R-34 Trainer question bank with folders and import.
- R-35 Student progress report with suggestions; code analysis (complexity, time, memory, comparison).

### Finance
- R-40 Fee plans with instalments, payments, receipts, reversal; outstanding computed from payments.
- R-41 Overdue reminders; finance dashboard.

### Certificates and placement
- R-50 Certificate completion rule checked live across services; student request, admin approval, PDF issue, public verification by code.
- R-51 Companies, jobs with enforced eligibility, applications, interview pipeline with audited stage changes; resume upload.

### Platform
- R-60 Role-based access, enforced in the API ([26-security.md](26-security.md)).
- R-61 One active login per user; optional human check on public forms.
- R-62 Notifications (in-app, announcements by audience and category, email through an outbox that never loses a message).
- R-63 Files with type and size rules and access control.
- R-64 Institute dashboard, audit log, and downloads: the audit log as CSV/Excel/PDF; students, leads, batch attendance, test results, dashboard figures and a student's progress report as CSV, the progress report also as a printed PDF; downloads are noted in the audit log ([31-downloads.md](31-downloads.md)).
- R-68 Extra duties: an administrator can give a person duties besides their main role (a trainer who also coordinates, a coordinator who also keeps the fees) and take them back; both take effect at the person's next request ([32-extra-duties.md](32-extra-duties.md)).
- R-65 Branding and customization by the admin ([27-design-system.md](27-design-system.md), [13-branding.md](13-branding.md)).
- R-66 Windows desktop app for secure tests.
- R-69 Android app (SutraVidya): sign in on the phone, then the whole website inside the app, so every web feature is there; camera, microphone, file upload and download work ([33-mobile-app.md](33-mobile-app.md)).
- R-67 Identity cards for students, trainers and staff: issued by ADMIN or COORDINATOR with a photo, printed one at a time or on A4 sheets, a QR code that opens a public check, withdrawn and replaced when lost ([30-id-cards.md](30-id-cards.md)).
- R-70 Legal pages and consent: public Privacy, Terms, Cookie and Refund pages linked from every sign-in page, a cookie notice, a Terms and Privacy tick before registering, a friendly empty state and a recoverable error screen instead of a blank page, and the name SutraVidya when the server cannot be reached ([34-legal-pages.md](34-legal-pages.md)).
- R-71 Public website: a front page with the institute's courses, what it offers, an enquiry form that feeds the leads list, contact details and a Login button ([35-public-website.md](35-public-website.md)).
- R-72 The administrator runs the website's content: announcement bar, hero text and picture, figures, gallery, trainers, hiring-partner logos, feedback, FAQ, course-card details and pictures, social links, a WhatsApp button, a Google map and an English/Marathi switch ([35-public-website.md](35-public-website.md)).

## 4. Non-functional requirements

| Id | Requirement | How it is met / checked |
|---|---|---|
| N-01 | A class signs in and takes a test together | k6 scripts and fixes in [25-load-testing.md](25-load-testing.md) |
| N-02 | No data loss on a dependency outage | Email outbox; Kafka events; circuit breakers with chosen failure behaviour per call |
| N-03 | Secure by default | [26-security.md](26-security.md) |
| N-04 | Each service deployable alone | Microservices, own database, Docker Compose first, Kubernetes later |
| N-05 | Runs on one modest host | About 20 containers, at least 6 GB for Docker; production additions in [07-deployment-guide.md](07-deployment-guide.md) |
| N-06 | Usable on a phone for students | Responsive layout, drawer menu ([27-design-system.md](27-design-system.md)) |
| N-07 | Behaviour is tested and documented | [06-test-plan.md](06-test-plan.md); a feature changes its doc in the same commit |

## 5. Departures from the original PDF

Recorded in [02-documentation-review.md](02-documentation-review.md): microservices instead of a monolith, live classes as core, one institute per deployment, plus the additions above (test portal, monitoring, code execution) that the PDF did not specify.

## 6. Out of scope for now

- Kubernetes manifests (Docker Compose is the supported deployment).
- Online payment gateway for fees (payments are recorded by finance staff).
- Remote control of a student's own computer (not possible from a browser; see "Open questions").

## 7. Open questions

- O6 student bulk import ([02-documentation-review.md](02-documentation-review.md)).
- Whether trainers should be able to help a student inside the student's own panel. A first version was built and withdrawn on 2026-11-01 because the real need (fixing a problem on the student's computer, for example installing a JDK) cannot be solved from inside a web page. Options: a screen-share request through LiveKit, or a self-hosted remote-help tool (MeshCentral or RustDesk). No decision yet.
- A real end-to-end live recording has not been run on the target host.

## 8. Status

All requirements above are implemented and deployed except where noted. Feature write-ups are numbered in `docs/` (08 to 25); the index is [README.md](README.md).
