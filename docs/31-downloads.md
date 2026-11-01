# Downloads and reports

What an administrator, coordinator or trainer can save as a file, where the button is, who may use it, and what is recorded.

## Every report has the institute's own name on it

Each report button opens a menu with three choices:

| Choice | What you get |
|---|---|
| **Excel (.xlsx), with letterhead** | The institute's logo, name, tagline and address at the top, the title, the facts the report covers, then the table with borders and a shaded header that stays in view. Numbers stay numbers. |
| **PDF or print, with letterhead** | The browser's print window on a page laid out like a college grade card: logo and name on the left, a heavy rule, the title in the middle, a small table of facts, then the data. Choose "Save as PDF" or a printer. A table with many columns prints across the page (landscape) in smaller type; long tables repeat their header and number their pages. |
| **CSV (plain data)** | Only the table, for another program. No letterhead. |

The letterhead is not typed anywhere in the reports: it is read from the **Branding** page (name, tagline, address, phone, email, website, logo). Change it there and every report, certificate and ID card follows. The audit log export, which the server builds, carries the same name in its Excel and PDF files.

The PDF goes through the print window, not a file save, so that names in any script (Marathi, Hindi, Tamil...) print correctly; the PDF library on the server only knows Latin letters.

## The downloads

| What | Where | Who | Format | What is in it |
|---|---|---|---|---|
| **All students** | Students page, "Download all students" | ADMIN, COORDINATOR | Excel, PDF, CSV | Full profile: code, name, email, phone, status, date of birth, gender, education, college, address, city, state, pincode, guardian and emergency contacts, admission date and source, remarks. Follows the search and status filter on the page. Up to 20,000 students. |
| **Leads** | Admissions page, "Download leads" | ADMIN, COORDINATOR | Excel, PDF, CSV | Name, phone, email, source, status, interested course, next follow-up, overdue, follow-up count, notes, lost reason, student it became, dates. Follows the filter. |
| **Attendance of a batch** | A batch page, "Download attendance" | ADMIN, COORDINATOR, TRAINER | Excel, PDF, CSV | Each student's sessions attended, sessions counted, percentage, and whether it is under the institute limit. |
| **Test results** | A test's page, "Download results" | the test's trainers, ADMIN, COORDINATOR | Excel, PDF, CSV | Each attempt: student, status, times, score, out of, percentage, pass mark, passed, rule violations, camera events, why it ended, typing review. A result that is not released yet is left blank. |
| **Dashboard figures** | Dashboard, "Download these figures" | ADMIN, COORDINATOR | Excel, PDF, CSV | Every number on the dashboard, one per row. |
| **Video lesson watch report** | A lesson's "who watched" page (course -> video lesson), "Download" | ADMIN, COORDINATOR, TRAINER | Excel, PDF, CSV | Lesson and course, then each student: percent watched, time played, video length, time the face was away, auto-pauses, status (completed / watching / not started), least watched first. |
| **Fee position of every student** | Finance desk, "Download every student's fees" | ADMIN, COORDINATOR, FINANCE | Excel, PDF, CSV | Every student with contact details and one row per fee plan (a student with no plan still has a row): net fee, paid, outstanding, overdue, next due. Totals only when all plans share one currency. |
| **Fee plans** | Finance desk, above the plan list, "Download fee plans" | ADMIN, COORDINATOR, FINANCE | Excel, PDF, CSV | Every plan of the chosen status (not only the page shown): total, discount, net fee, paid, outstanding, overdue, status, next due. |
| **Overdue instalments** | Finance desk, above the overdue list, "Download overdue" | ADMIN, COORDINATOR, FINANCE | Excel, PDF, CSV | Each overdue instalment, longest overdue first, with the student, due date, days overdue and amount. |
| **Student progress report** | A student's page and "My progress": "Download or print" | staff and the trainer of that student's batch; the student for their own | Excel, PDF (print), CSV | The student's details in a table under the title, like a grade card, then the same sections as the screen: profile, indicators, enrollments, course progress, attendance, live classes, tests, coding, assignments, suggestions, and which parts could not be loaded. |
| Audit log | Audit Logs page | ADMIN | CSV, Excel, PDF | Unchanged: built on the server. |
| Certificates, ID cards, class transcript and recording, question bank templates | their own pages | as before | PDF, text, Excel, ZIP | Unchanged. |

CSV opens in Excel and Google Sheets (the file starts with a byte-order mark so names in any script read correctly). The real Excel file keeps text as text, so a name that starts with `=` is never run as a formula there.

## How they are made

- **Students** are read from the server in one call (`GET /api/students/export`), because the full profile is only on the student record. The server limits it to ADMIN and COORDINATOR, caps it at 20,000, and **writes an audit entry** (`STUDENTS_EXPORTED`, with how many rows and the filter).
- **The others** are built in the browser from the screens' own API calls (leads page by page, a batch's attendance, a test's results, the dashboard summary, a progress report). So a person can only download what the API already shows them. The row layouts are in `frontend/src/lib/export-rows.ts`, tested without a browser.
- **Excel and PDF** are made in the browser from the same rows (`lib/report.ts` builds the print page, `lib/report-xlsx.ts` the Excel cells, `lib/report-output.ts` saves and prints). The print page is drawn in a hidden frame, every value is escaped so a name such as `<script>` prints as text, and the Excel writer is loaded only when someone asks for Excel.
- The **audit log** is still built on the server (CSV, Excel, PDF); the server reads the institute's name from identity-service for the top of the Excel and PDF.

## What is recorded

After a browser-built download, the page tells the server (`POST /api/reports/exports`: which report, how many rows, which batch or test) and the server adds a line to the audit log: `REPORT_DOWNLOADED`, who, when, what, how many. A student downloading their own progress report is not logged. The lines can be read on the Audit Logs page and exported with it.

This is a record, not a lock: the file is built in the browser, so a person who blocks that one call would still get the file. The students list, the one with the most personal data, is logged by the server itself and cannot be skipped.

## Safety

- **Spreadsheet formulas.** A name such as `=HYPERLINK("http://...")` would run when an administrator opens the file. A cell that starts with `=`, `@`, a tab or a return, or with `+` or `-` that is not a plain number or phone number, gets a leading apostrophe. Phone numbers such as `+91 98765 43210` are left as they are (`lib/download-csv.ts`, tested).
- **No file is kept on the server.** Nothing is stored; the file exists only in the person's browser.
- **Keep downloaded files safe.** A student export holds addresses and guardians' phone numbers. Treat it like the database: do not email it, do not leave it in Downloads.

## Not available, on purpose: a database backup from a web page

A button that makes and downloads a copy of the whole database would put every password hash, token hash, fee record and student address one stolen administrator login away from leaving the building. Backups are made on the server instead (`pg_dump`, see [07-deployment-guide.md](07-deployment-guide.md)) and, once automated, copied to a second place. If the institute needs an administrator-triggered backup, the safe form is a button that asks the server to make a backup on the server and shows its time and size, without letting anyone download it from the web.

## Not built

- A PDF file that is saved without the print window. It would need a font for each script; the print window already has them.
- Server-built Excel or PDF for the long lists. They are built in the browser, which is fine for thousands of rows; a very large institute would move them to the server's `TabularExporter`.
- A logo in the audit log files: the server writes the name and address, not the picture.
- A whole-institute progress report for every student at once.
- Attendance by date range or by session across batches.
