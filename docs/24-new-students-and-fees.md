# 24 - New students, the course list, and course fees

## Self-registration is a switch
Public sign-up is **off by default** (`itilms.security.self-registration-enabled`). While it is off the register page says *Online registration is not open*. In Docker it is `SELF_REGISTRATION_ENABLED` in `deploy/docker/.env` (passed to identity-service by `docker-compose.yml`; documented in `.env.example`). To open it, set `SELF_REGISTRATION_ENABLED=true` and recreate identity-service:

```
docker compose up -d --no-deps identity-service
```
On a public server turn the human check on first ([docs/22](22-human-check-captcha.md)).

## What happens when a new student appears
1. **Staff are told.** admission-service sends a notification to ADMIN and COORDINATOR, *"New student: Name"*, with a link that opens the student. This happens for a student who registered themselves and for one an admin admitted **without** a batch. A student admitted with a batch is already placed and needs no notice.
2. **The student sees only the course list** until they are placed. The sidebar shows **Dashboard** and **Courses**, and the dashboard explains the next step. The check is `GET /api/batches/mine/placed` (batch-service): *has this student ever had an enrolment, in any status*. It is deliberately not "has an active batch", so a student whose batch ended keeps certificates, placements and the rest. If the check cannot be read the student is treated as placed, so a failed lookup never locks anyone out. Other pages stay reachable by URL; a student who is not placed has no data on them.
3. **The student asks for a course.** On a course page, **Request to join** (optionally a preferred batch and a message). Staff get a *New course request* notification.
4. **Staff approve.** Sidebar **Course requests**, tab **Pending**, **Approve**, pick a batch (the one asked for is preselected; only Planned or Ongoing batches are listed). The student is enrolled, is told, and the full menu appears (minus whatever the administrator hid for students, [docs/13](13-branding.md)). **Reject** needs a reason, which the student sees.
5. **Fees.** Approving does not create a fee plan. The notification tells staff to set it up; they do it from Finance for the student.

## Course fee
A course carries one list price, `fee` (INR). Before this change there was no field for it in the web app.
- **Staff** set it in the *New course* dialog and in the course editor (**Course fee (INR)**). Empty means *not priced*, which is shown to students as *To be confirmed by the institute*, not as free.
- **Students** see it on the course list card, on the course page (*Course fee*), and in the *Request to join* box, which also says that the institute sets up the fee plan once they are placed and that instalments and due dates then appear under **Finance**.

### Not covered
The course has only a total. Instalments (how many, when) are decided per student when finance raises the fee plan, so they cannot be shown before the student is placed. Showing a standard schedule up front would need a course-level instalment setting.
