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

### Finance desk additions (2026-11-12)

All under `/api/fees/...` (already routed). Migrations V5 to V11 in finance-service.

| What | How it works |
|---|---|
| **Receipt in the college layout** | Logo and name, "Student Fees Receipt", a boxed block (receipt no, date, student, reg no, course, batch, financial year April to March), a particulars table, total in words, payment mode with reference, bank line, remarks. The **particulars** list each fee head the payment went towards: a payment covers the stretch of the installment queue (oldest due first) after the money paid before it, so a development fee shows as its own line. The **bank** line is `INSTITUTE_BANK`; the batch comes from batch-service. |
| **Fee structure per course** (V5) | `PUT /api/fees/templates/{courseId}` (ADMIN, FINANCE): installments, days to the first due date, and extra heads. A student placed in a batch gets a plan that follows it. Plans already raised are not changed. |
| **Statement** | `GET /api/fees/students/{id}/statement`: every course, every payment, the balance, as a PDF. A student only their own. |
| **Collection report** | Finance page: payments between two dates by method, with a download. Built from `GET /api/payments`. |
| **Reminders** | The morning scan (already there) now emails the student as well as notifying them, naming the fee head. |
| **Refunds** (V6) | The finance desk asks from a payment; **only the administrator** approves or rejects. Approving adds to `payments.refunded_amount`; what a student has paid is the payment minus that, so the fee is owed again and the dashboard follows. The money is handed back outside the system. |
| **Concessions** (V7) | A discount needs a reason (kept on the plan). One above `discount-approval-percent` (10) of the fee needs the administrator. Only a new or changed discount is checked. |
| **Late fee** (V8) | Off unless `LATE_FEE` is above 0. Once an installment is unpaid more than `LATE_FEE_AFTER_DAYS` (7) days, a flat charge named "Late fee" is added once. The administrator can waive one nothing has been paid on. |
| **Day-end cash count** (V9) | The desk counts the drawer; the system says how much cash it recorded that day. A difference needs a note. One closing per day. |
| **Expenses** (V10) | By category (salary, rent, utilities, marketing, supplies, maintenance, other). Voided with a reason, never deleted. Profit is fees collected (net of refunds) less expenses. ADMIN and FINANCE only. |
| **UPI by QR** (V11) | With `INSTITUTE_UPI_ID` set, the student sees a QR for the amount, pays from their own app and sends the UTR. The desk matches it with the bank and **confirms**, which records an ordinary UPI payment with a receipt; or rejects it. The same UTR cannot be claimed twice. |
| **Fees due** | The student's dashboard lists the next fee dates, how far away and what is left. Hidden with the dashboard setting `fee-due`. |

Not done: the admission-time discount (set by the coordinator when converting a lead) is not subject to the 10% rule; only changes made on the finance side are.

### Payroll and the finance overview (2026-11-12)

- **Staff** means every active user who is not a student. identity-service gives the list (`GET /api/users/staff-directory`, ADMIN or FINANCE); finance-service keeps only each person's pay.
- **Salary structure** (`staff_pay`, V12): monthly basic, allowances, a fixed deduction, and whether the person is on payroll. A person with a basic of 0 gets no slip.
- **A month** (`/app/finance/payroll`, ADMIN and FINANCE): *Make this month's slips* creates one slip per person on payroll who has none (never for a month that has not started). A slip copies the figures, so changing a structure later does not rewrite slips already made. *Adjust* sets leave without pay (days), another deduction (advance, fine), a bonus and a remark, while the slip is still pending.
- **Maths** (`PayslipMath`): gross = basic + allowances + bonus. Leave without pay is charged at (basic + allowances) / days of the month per day. Net = gross - fixed deduction - leave deduction - other deduction, and is refused if it would be below zero. Leave is entered by the desk; it is not read from attendance.
- **Status:** PENDING, then APPROVED by the administrator, then PAID by the desk with a method and an optional reference. Paying records a `SALARY` expense, so profit and loss already counts it, and tells the person their slip is ready. Approving and paying work for one slip or the whole month.
- **Salary slip PDF:** the same plain boxed layout as the receipt: logo and name, slip no, employee, designation, month, earnings beside deductions, net pay in words, and how and when it was paid. The finance desk can download any slip; a member of staff only their own, and only once paid (**My salary**, every staff role).
- **Totals:** the month shows total salary, paid, still to pay and people; the year lists each month. The Finance page now has a *Finance overview* (fees collected and still to come as a percent, this month's income, salary, expenses and profit) with a download, and every fee download has paid and balance percent, discount and its reason, and installments paid.
- Not done: no PF or TDS (use *other deduction*), no automatic leave from attendance, no stamp on the slip.

### Completing the finance department (2026-11-12)

Migrations V13 to V16 in finance-service.

| What | How it works |
|---|---|
| **Backups** | `deploy/scripts/backup-databases.sh` (see [07](07-deployment-guide.md) section 8): every `itilms_*` database daily into `~/backups/YYYY-MM-DD/`, tested, 14 days plus the first of each month for a year, optional off-server copy with rclone. Run from cron at 02:30 on the server. Uploaded files are not included. |
| **Refund pay-out** (V13) | An approved refund is not finished until it is marked **paid** (method, date, reference, by whom). The books show it as paid out, and a cash refund reduces that day's expected cash in the day-end count. |
| **Closing the books** (V14) | The administrator closes the books through a date (up to yesterday). Nothing dated on or before it can be recorded, reversed, voided, paid out or counted. Moving the date back needs a note; every move is audited. |
| **Salary history** (V15) | Every time a person's pay is set it is kept with who and when; **History** beside the person on the payroll page. |
| **GST** (V17) | The administrator sets the rate, GSTIN, SAC code and the date it applies from on the Finance page (**GST**; `PUT /api/fees/gst/settings`, ADMIN only; 0 switches it off). Until saved there, `GST_PERCENT`, `INSTITUTE_GSTIN` and `INSTITUTE_SAC` are used. Fees are taken as tax-inclusive. **From its start date only:** a payment, receipt (reprints too) or book entry dated earlier shows no GST, so turning GST on does not rewrite history; a rate starting inside a closed period is refused. The receipt shows taxable value, CGST, SGST, GSTIN and SAC; **GST on fees** gives the tax per month, net of refunds, with a download. One rate for every fee, CGST and SGST only (no IGST), no input credit on expenses, no GST return format. |
| **Bank reconciliation** (V16) | Import the bank's statement CSV (Date, Narration, Reference, Debit, Credit; dates as 12/03/2026, 2026-03-12 or 12-Mar-2026). Lines are matched to payments (money in) and expenses or non-cash refunds (money out) by a reference found in the narration, or else by a single entry of the same amount within 3 days; two equal candidates are never guessed between. The rest is listed: in the bank but not the books, and in the books as bank money but not on the statement. Match or undo by hand. Importing the same file twice adds nothing. |
| **Books** (`/app/finance/books`) | Double-entry journal, trial balance, profit and loss and balance sheet, **derived** from fee plans, payments, refunds and expenses, so nothing is posted twice and nothing can disagree with the records. Every entry balances; the page says so. Accounts: cash, bank, UPI and online, fees receivable, GST payable, refunds payable, fee income, discounts, and expenses by category. |

What the books are not: fees are income when the plan is raised and money in when received. Extra charges, late fees and a changed discount are booked on the plan's creation date, because the plan keeps no history of when they were added. Salary is an expense when paid, not when earned. An expense is credited to cash or bank by the salary's payment method, otherwise to "Cash or bank, source not recorded". There is no accrual, depreciation or tax filing: it is a record the institute's accountant can start from, not a replacement for them.

### Not covered
The course has only a total. Instalments (how many, when) are decided per student when finance raises the fee plan, so they cannot be shown before the student is placed. Showing a standard schedule up front would need a course-level instalment setting.

## Finance download (2026-10-30)
The finance desk has **Download all students (CSV)**. One row per fee plan with student code, name, email, **phone**, student status, plan status, currency, net fee, paid, outstanding, overdue and next due date; a student without a plan still gets a row. It is built in the browser from `GET /api/students` and `GET /api/fee-plans` (both allow FINANCE), read page by page, and saved with a UTF-8 BOM so Excel shows non-English names correctly.
