# 38 - A batch with several courses

A batch is created for one course and can then hold more: Java now, SQL and Advanced Java next to or after it, each taught by its own trainer. A course can still be sold on its own. This page is the rule book; the endpoints are in [04-api-reference.md](04-api-reference.md) and the tables in [03-erd.md](03-erd.md).

## The rules

1. **The batch is sold as a whole.** `batches.fee` is the price of the batch. A student placed in the **whole batch** pays that fee and nothing more for any course in it, however many are added later. Empty means the course fee applies, exactly as before.
2. **A course is sold on its own.** A student placed in **one course** of the batch (`enrollments.course_only`) pays that course's own fee and has only that course. The courses added to the batch later do not reach them.
3. **Adding a course enrols the whole batch.** `POST /api/batches/{id}/courses` creates an enrolment for the new course for every student placed in the whole batch, and for every student who joins later. These are **included** enrolments (`enrollments.included`): no fee, no seat, no row on the roster. course-service turns each into lessons and progress through the usual `enrollment-created` event.
4. **Each added course has its own trainer.** `batch_courses.trainer_id`. The batch's own course keeps the trainer on the batch row (change it by editing the batch). A trainer of an added course is attached to the batch, so they can mark its registers.
5. **A lecture belongs to a course.** `class_sessions.course_id`; empty means the batch's own course. The lecture is taught by that course's trainer, and its register is the students who have that course in the batch.
6. **One certificate for the batch.** A student who holds several courses in a batch is eligible when every one of them is finished (criterion `OTHER_COURSES`, on top of the usual lessons, tests, attendance and fees). The certificate is for the batch: its name, or the course titles together when it has none. A student who holds one course gets that course's certificate. Wording written on the course ([14](14-certificate-workflow.md)) is the wording of the batch's own course.

## How it travels

`EnrollmentCreatedEvent` gained two fields, both absent for an ordinary placement:

| Field | Meaning | Who reads it |
|---|---|---|
| `feeIncluded` | The student has this course only because the batch holds it | finance-service raises no fee plan |
| `feeOverride` | The batch fee, when the student is placed in the whole batch | finance-service charges it instead of the course fee |

A student's fee plan is still one per course. Placed in the whole batch, the plan is for the batch's own course at the batch fee; the included courses have none.

## What changes for people

- **Staff:** the batch page has **Courses in this batch** (add a course with its trainer, change a trainer, take a course out), **Batch fee**, and the enrol dialog asks *the whole batch* or *one course only*. Scheduling a lecture asks which course.
- **Trainers:** see the batches where they teach any course. A lecture shows its own course.
- **Students:** see every course of their batch under **Courses**, with their own progress in each.
- **Course requests and online purchases** place the student in that one course, at its fee.

## Edge cases that are decided

- Dropping a student closes their included courses too; transferring one moves them and re-creates the included courses of the target batch.
- Taking a course out of a batch closes the included enrolments for it and **keeps** the students' progress; lectures already held for it stay in the timetable.
- Changing `batches.fee` applies to students placed from then on. A fee plan already raised is not changed (the finance desk edits a plan).
- Old batches have no added courses, no fee, and behave as they always did.

## Departure from the PDF

The PDF gives a batch one course. This is recorded in [02-documentation-review.md](02-documentation-review.md).
