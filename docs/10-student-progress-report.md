# Student progress report

One page that answers "how is this student doing, and what should they do next?" A student opens it as **My progress**; staff and trainers open it under a student (Students, then the student). Endpoints are in `04-api-reference.md`.

## What is in it

| Section | Comes from | Notes |
|---|---|---|
| Profile | admission-service | name, code, contact, status |
| Batches | batch-service | every enrolment, current and past, with course, trainer, dates |
| Attendance | reporting-service's copy of the register | per batch and overall; **late counts as attended, excused absences are left out**, as batch-service counts them |
| Course progress, modules, recorded lessons | course-service | mandatory lessons finished per course and per module; video lessons finished |
| Live classes | liveclass-service | finished live classes in the student's batches, how many they joined, minutes in the room |
| Tests | assessment-service | best result on each test, passed, average, tests below the pass mark, tests not yet taken, attempts ended by the secure-test rules |
| Coding | assessment-service | test cases passed on the best attempt at each coding question, and the questions not fully solved |
| Assignments | assessment-service | set, handed in, marked, average mark, sent back, late, still to hand in |

## Headline numbers

Seven percentages, each with a band (good 75 and over, fair 50 to 74, low under 50): course progress, attendance, live class participation, recorded lessons, test performance, coding, assignments. **Attendance is judged against the institute's own line** (`itilms.reporting.attendance-threshold`, default 75), not the generic bands. A number with nothing behind it (no tests taken, no live class held) is shown as **no data yet**, never as 0%: a new student has not failed anything. A course the student dropped is left out of course progress and recorded lessons.

## Suggestions

Worked out from the numbers, most urgent first, at most eight. Each names the fact it comes from, so a student can see why they are being told, and links to the page to act on it.

| Suggestion | When |
|---|---|
| Submit / hand in the overdue assignment | an assignment is still to hand in (overdue ones first) |
| Redo the assignment sent back | a submission was returned for rework |
| Attempt the pending test | an open test for their batch has not been taken |
| Revise before retaking | a test is below its pass mark |
| Practice coding | coding average is under 60% (names the question they did worst on) |
| Complete the pending module | an active course has an unfinished module (the first one) |
| Watch the recorded lessons | under half of the recorded lessons are finished |
| Improve your attendance | attendance is below the threshold, over at least 3 classes |
| Join more live classes | joined under 60% of at least 3 live classes |
| Stay in the test window | an attempt was ended for leaving the window |

When none applies the report says **you are on track**. Nothing is suggested from missing data.

## Who sees what

- A **student** sees only their own (`/me/progress`). Their copy **holds back the results of tests whose trainer has not released them**, exactly as their results page does: such a test is counted as "result pending" and its score is in no number, list or suggestion.
- **Administrators and coordinators** see any student, with every result.
- A **trainer** sees a student only if the student is enrolled in a batch the trainer teaches (a completed batch counts), checked against batch-service at the moment of asking. If that cannot be checked the answer is no.
- Other roles (finance, placement) do not see academic progress.

## How it is put together

reporting-service asks each owning service for its part, over `/internal/` endpoints that the gateway hides from the outside. Those endpoints do not check who is asking; **reporting-service is the one place that decides who may see whose report**, and it does so before it asks. Nothing is stored: the report is worked out on each request. If a service is down, that section is left out and named in `unavailable`; the rest of the report is still returned and the page says what is missing.

Attendance is the exception: it is read from reporting-service's own copy of the register, so it is only as current as the events it has received.
