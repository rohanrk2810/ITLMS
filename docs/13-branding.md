# Institute branding

One deployment = one institute, so branding is a single row (`institute_settings`, identity-service, V3) rather than a per-tenant table. Another institute runs its own stack and database, which is what keeps one institute from seeing another's settings. (The codebase has no tenant concept; this was a deliberate choice on 2026-09-30, not a retrofit of `tenant_id`.)

- **Read (anonymous):** `GET /api/public/branding` (name, tagline, colour, contact, signatory, image URLs), `.../logo`, `.../favicon`. The gateway routes these without the sign-in rate limit.
- **Write (ADMIN only, enforced in the service):** `PUT /api/branding`, `POST|DELETE /api/branding/logo|favicon`. Audited (`BRANDING_*`).
- **Images** live in the table (the login page needs them before any token exists). Type is decided from the file's bytes; SVG is refused (script risk); max 1 MB. URLs carry `?v=<version>` so browsers cache them long and still refetch after an edit.
- **Frontend:** `BrandingEffect` sets the tab title, favicon and brand colour; `BrandMark` shows logo + name in the header, sidebar and login page; the admin edits it on the **Institute** tab of `/app/customize` (until 2026-10-31 a page of its own at `/app/branding`; that address now redirects). Nothing hard-codes a name any more; the seeded default ("IT Institute LMS") is data in the migration.
- **Caching:** `GET /api/public/branding` is read on every page load, so identity-service keeps the row in memory for 10 seconds (cleared at once by any admin change) and answers with `Cache-Control: max-age=15, public`. An edit therefore shows for everyone within about 15 seconds, and for the admin who made it straight away. The images are cached for 30 days under their `?v=` address.
- **Emails:** notification-service reads the name from identity (cached 60 s, falls back to `MAIL_FROM_NAME`).
- **Certificates:** will read name, logo and signatory from the same endpoint (certificate workflow phase).

## Customize the portal (2026-10-30)

Admin page `/app/customize` (menu item **Customize**, ADMIN only). It has two tabs, chosen with `?tab=`: **Institute** (name, tagline, brand colour, contact, signatory, student menu, logo, favicon, login background; the former Branding page, with its own *Save branding* button) and **Portal** (everything below, with one *Save* button for the whole tab). Save one tab before switching to the other: an unsaved edit is lost on switching. `/app/branding` redirects here.

- **Storage:** one JSON document in `institute_settings.ui_config` (identity-service, Flyway V10, default `{}`), returned as `uiConfig` text on `GET /api/public/branding`. Written by `PUT /api/branding/ui-config` (ADMIN only, body `{ "config": "<json>" }`, at most 20,000 characters, must be a JSON object; audited as `BRANDING_UI_CONFIG_UPDATED`). The server only stores it; the browser (`lib/ui-config.ts`) reads and applies it and ignores anything of the wrong shape.
- **Theme:** main colour (overrides the Branding colour), dark-mode look (original warm black, pure black, charcoal, navy, forest), light-mode look (original cream, white, grey, sky, mint), corner roundness, text size 85-125%, and a default mode (device / light / dark) that applies until a person uses the light/dark toggle.
- **Menus, per role:** hide items, reorder them and rename them. The dashboard and the Customize page can never be hidden, so an administrator cannot lock themselves out. This sits on top of the older student-menu setting and per-student menu.
- **Dashboard:** the eight figures, the charts, the audit-trail link, quick links, student charts and announcements can each be hidden.
- **Login page:** a welcome line above and a footer line below the sign-in form.
- **Words:** show a word differently everywhere on screen (for example Student to Learner; plurals follow). A MutationObserver rewrites visible text only; form fields, code, editors and stored data are untouched. Browser-tab titles, PDFs and e-mails are not changed.
