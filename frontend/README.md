# IT-ILMS Frontend

React + TypeScript + Tailwind CSS v4 + shadcn/ui, talking to the backend only
through the API gateway (`VITE_API_BASE_URL`, default `http://localhost:8080`).

## What's here so far

- **Auth**: sign in, self-registration (with a clear message when it's
  closed - off by default on the backend), forgot/reset password, a forced
  change-password page for a temporary/reset password (`ProtectedRoute`
  redirects every route there until `mustChangePassword` clears), silent JWT
  refresh (`src/api/client.ts`, `src/api/auth.ts`). The access token lives in
  memory only; the refresh token is the one thing kept in `localStorage`.
- **Role-based layout and routing**: `src/layouts/app-layout.tsx` renders a
  sidebar filtered by the signed-in user's role (`src/components/nav-items.ts`);
  `src/routes/protected-route.tsx` redirects an unauthenticated visitor to
  `/login` and an out-of-role one to a "not available" page. This is UX only -
  every API call behind it is checked again by the owning service.
- **API client**: `src/api/client.ts` (axios, attaches the bearer token,
  retries once through a shared refresh on a 401), `src/api/types.ts` mirrors
  the backend's `ErrorResponse`/`PageResponse` shapes.
- **Notification bell and email links**: `src/components/notification-bell.tsx`
  polls `/api/notifications/unread-count`, loads the list on open, and opens a
  notification's `actionUrl` through `src/lib/action-url.ts` (backend links
  use `/student/...`, `/live/...` etc.). An email link lands on the same
  backend path with no `/app` prefix at all, so `App.tsx`'s catch-all route
  runs it through the same mapper before falling back to the 404 page -
  `mapActionUrl` is the one place either caller needs.
- **Student pages**: my courses + a lesson player (video/PDF/notes/link,
  progress tracked as it plays), tests (timed attempts, autosave, results),
  live class join (LiveKit's own `VideoConference` component), fees
  (installments, payment history, a printable receipt), certificates (earned
  + an eligibility checklist with a claim button for courses still in
  progress).
- **Staff pages**: admissions (leads, follow-ups, admit-to-student), students
  (search, profile, standing), batches (roster, timetable, scheduling a
  session), attendance marking (a register per session, corrections require a
  reason per Doc S14), the finance desk (dashboard, overdue list, a fee-plan
  page to record/reverse payments and cancel a plan), user management
  (create trainer/finance/placement/coordinator accounts, status, password
  reset - `/api/users`, ADMIN only for anything that changes state), and
  trainer/staff authoring for courses (modules, lessons, PDF upload through
  file-service, publish/archive) and tests (questions with an answer key,
  publish/close, a results sheet). Assignment submit (student, with file
  attachments) and evaluate/return-for-rework (trainer) share one detail
  page, reached from the Assessments tab or a batch's own assignment list.
  Courses, tests, live classes and finance each render a different page for
  the same route depending on role - `*-index-page.tsx` in each folder is the
  switch - so a student's and a trainer's (or a finance officer's)
  experience never need two different URLs.
- **Dashboard and audit log**: ADMIN/COORDINATOR see the institute-wide
  counters from reporting-service (`/api/dashboard/summary`) instead of the
  generic per-role blurb; ADMIN also gets `/app/audit-logs`
  (`/api/audit-logs`, search plus CSV/Excel/PDF export via
  `/api/reports/audit-logs/export`), linked from the dashboard.
- Every route-level page is lazy-loaded (`React.lazy` + `Suspense` in
  `App.tsx`), so a role that never opens a module never fetches its code, and
  the LiveKit room (the one genuinely large dependency) loads only when
  someone actually joins a class.
- Remaining placeholders: placements, files, announcements.

## Commands

```bash
npm install
cp .env.example .env.local   # only if the gateway isn't on localhost:8080
npm run dev                  # http://localhost:5173
npm run build                # tsc -b && vite build
npm run lint
```
