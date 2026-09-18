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
- **Notification bell**: `src/components/notification-bell.tsx`, polling
  `/api/notifications/unread-count`, loading the list on open, and opening a
  notification's `actionUrl` through `src/lib/action-url.ts` (backend links
  use `/student/...`, `/live/...` etc.; this is the one place that maps them
  onto this app's `/app/...` routes).
- **Student pages**: my courses + a lesson player (video/PDF/notes/link,
  progress tracked as it plays), tests (timed attempts, autosave, results),
  live class join (LiveKit's own `VideoConference` component), fees
  (installments, payment history, a printable receipt), certificates (earned
  + an eligibility checklist with a claim button for courses still in
  progress).
- Every page is lazy-loaded (`React.lazy` + `Suspense` in `App.tsx`), so a
  role that never opens a module - Finance for a student, say - never
  fetches its code, and the LiveKit room (the one genuinely large dependency)
  loads only when someone actually joins a class.
- Every other sidebar module (admissions, students, batches, placements,
  files, announcements) is still a placeholder page - routing and role
  gating work end-to-end, the pages themselves are next.

## Commands

```bash
npm install
cp .env.example .env.local   # only if the gateway isn't on localhost:8080
npm run dev                  # http://localhost:5173
npm run build                # tsc -b && vite build
npm run lint
```
