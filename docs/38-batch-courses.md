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

## Selling a batch online

The website's **Courses** page lists, above the courses, the batches that can be bought: planned or ongoing, with a batch fee set and a free seat (`GET /api/public/batches`, open to anyone, no student names). A card shows the courses in the batch, the dates, days and time, the seats left and the fee, with **Buy** and **Inquiry**.

**Buy** works like buying a course ([34](34-online-payments.md)), with one difference: `POST /api/payments/online/orders {courseId, batchId, wholeBatch: true}`.

1. finance-service reads the batch from batch-service and refuses unless it is open, has a fee and has a free seat. The amount is the batch fee, never anything from the browser; the order remembers the batch (`online_orders.whole_batch`).
2. After Razorpay's signature checks out, the fee plan is raised for the batch's own course at the batch fee, and the payment and receipt are recorded.
3. batch-service `paid-join` sees `wholeBatch` on the order and places the student in **exactly that batch**, in the whole of it: every course in it, no further fee. If the seat has gone, the order stays PAID and ADMIN and COORDINATOR are told, as for a course.

A batch with no fee is not listed and cannot be bought online; the course page and the inquiry form still work for it.

## What changes for people

- **Staff:** the batch page has **Courses in this batch** (add a course with its trainer, change a trainer, take a course out), **Batch fee**, and the enrol dialog asks *the whole batch* or *one course only*. Scheduling a lecture asks which course.
- **Trainers:** see the batches where they teach any course. A lecture shows its own course.
- **Students:** see every course of their batch under **Courses**, with their own progress in each.
- **Course requests and online course purchases** place the student in that one course, at its fee. **Buying a batch online** places them in the whole batch, at the batch fee.

## Edge cases that are decided

- Dropping a student closes their included courses too; transferring one moves them and re-creates the included courses of the target batch.
- Taking a course out of a batch closes the included enrolments for it and **keeps** the students' progress; lectures already held for it stay in the timetable.
- Changing `batches.fee` applies to students placed from then on. A fee plan already raised is not changed (the finance desk edits a plan).
- Old batches have no added courses, no fee, and behave as they always did.

## Departure from the PDF

The PDF gives a batch one course. This is recorded in [02-documentation-review.md](02-documentation-review.md).
