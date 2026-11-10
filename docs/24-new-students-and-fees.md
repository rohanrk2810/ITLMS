# 24 - New students, the course list, and course fees

## Self-registration is a switch
Public sign-up is **off by default** (`itilms.security.self-registration-enabled`). While it is off the register page says *Online registration is not open*. In Docker it is `SELF_REGISTRATION_ENABLED` in `deploy/docker/.env` (passed to identity-service by `docker-compose.yml`; documented in `.env.example`). To open it, set `SELF_REGISTRATION_ENABLED=true` and recreate identity-service:

```
docker compose up -d --no-deps identity-service
```
On a public server turn the human check on first ([docs/22](22-human-check-captcha.md)).

## What happens when a new student appears

*The student's details are taken first.* Registration and the staff Admit dialog both require the full profile (date of birth, gender, education, address, PIN, guardian) before the login account is created. The student adds their own ID card photo once on **My profile**; after that only staff can change it ([docs/30](30-id-cards.md)).
1. **Staff are told.** admission-service sends a notification to ADMIN and COORDINATOR, *"New student: Name"*, with a link that opens the student. This happens for a student who registered themselves and for one an admin admitted **without** a batch. A student admitted with a batch is already placed and needs no notice.
2. **The student sees only the course list** until they are placed. The sidebar shows **Dashboard** and **Courses**, and the dashboard explains the next step. The check is `GET /api/batches/mine/placed` (batch-service): *has this student ever had an enrolment, in any status*. It is deliberately not "has an active batch", so a student whose batch ended keeps certificates, placements and the rest. If the check cannot be read the student is treated as placed, so a failed lookup never locks anyone out. Other pages stay reachable by URL; a student who is not placed has no data on them.
3. **The student asks for a course.** On a course page, **Request to join** (optionally a preferred batch and a message). Staff get a *New course request* notification.
4. **Staff approve.** Sidebar **Course requests**, tab **Pending**, **Approve**, pick a batch (the one asked for is preselected; only Planned or Ongoing batches are listed). The student is enrolled, is told, and the full menu appears (minus whatever the administrator hid for students, [docs/13](13-branding.md)). **Reject** needs a reason, which the student sees.
5. **Fees.** Approving does not create a fee plan. The notification tells staff to set it up; they do it from Finance for the student.

## Course fee
A course carries one list price, `fee` (INR). Before this change there was no field for it in the web app.
- **Staff** set it in the *New course* dialog and in the course editor (**Course fee (INR)**). Empty means *not priced*, which is shown to students as *To be confirmed by the institute*, not as free.
- **Students** see it on the course list card, on the course page (*Course fee*), and in the *Request to join* box, which also says that the institute sets up the fee plan once they are placed and that instalments and due dates then appear under **Finance**.

### The fee shows up when the student is placed (2026-11-11)

Before this, a student approved for a class who had paid nothing saw nothing under **Fees**: no amount to pay and no Pay online button, and the finance desk had no record of what was owed. Now, when a student is placed in a batch (a coordinator enrols them, approves their course request, or moves them), finance-service raises the course fee as a fee plan:

- It listens to the `enrollment-created` event (which now carries the student's name and code, because a Kafka consumer has no sign-in to ask for them) and reads the course fee from the public course page.
- The plan is **one installment** for the whole course fee, due **7 days** after placement, with no discount. The finance desk can change the fee, the discount or the split afterwards (**Change a plan**). A course with no fee raises no plan.
- It never overwrites anything: a student who already has a live plan for the course (from admission or a purchase) keeps it, and the plan only learns the batch. If the placement arrives *before* the admission, the plan it raised is flagged `auto_created`; an admission that then arrives with an agreed discount or schedule replaces it, provided nobody has paid on it or changed it.
- If the course fee cannot be read at that moment (course-service is down), the event is retried rather than lost.

### Extra charges: development fee, a book, an exam fee (2026-11-11)

The finance desk adds a named charge on top of the course fee, for one student or for a whole batch:

- **One student:** **Add charge** on the fee plan page. **A whole batch:** **Charge a batch** on the Finance page. Each asks for what it is for, the amount and a pay-by date (not in the past).
- A charge becomes an **installment with a name** (`fee_installments.label`), so it is paid, receipted and reminded about like any other part of the plan, and **Pay online** works for it. The plan's total grows by the amount; a plan that was fully paid owes money again. The student sees it under **Fees** as its own line and gets a notification.
- A batch charge reaches every student of that batch who has a non-cancelled fee plan, found through the plan's batch (a placement or a transfer records it). Students with no plan are not charged: the desk is told how many were reached.
- **Changing a plan later keeps the charges.** The desk edits the plan's total as before; extra charges stay as they are and only the course-fee installments are re-split.
- Audited as `FEE_CHARGE_ADDED` and `BATCH_CHARGE_ADDED`.

### Not covered
The course has only a total. Instalments (how many, when) are decided per student when finance raises the fee plan, so they cannot be shown before the student is placed. Showing a standard schedule up front would need a course-level instalment setting.

## Finance download (2026-10-30)
The finance desk has **Download all students (CSV)**. One row per fee plan with student code, name, email, **phone**, student status, plan status, currency, net fee, paid, outstanding, overdue and next due date; a student without a plan still gets a row. It is built in the browser from `GET /api/students` and `GET /api/fee-plans` (both allow FINANCE), read page by page, and saved with a UTF-8 BOM so Excel shows non-English names correctly.
