# Institute branding

One deployment = one institute, so branding is a single row (`institute_settings`, identity-service, V3) rather than a per-tenant table. Another institute runs its own stack and database, which is what keeps one institute from seeing another's settings. (The codebase has no tenant concept; this was a deliberate choice on 2026-09-30, not a retrofit of `tenant_id`.)

- **Read (anonymous):** `GET /api/public/branding` (name, tagline, colour, contact, signatory, image URLs), `.../logo`, `.../favicon`. The gateway routes these without the sign-in rate limit.
- **Write (ADMIN only, enforced in the service):** `PUT /api/branding`, `POST|DELETE /api/branding/logo|favicon`. Audited (`BRANDING_*`).
- **Images** live in the table (the login page needs them before any token exists). Type is decided from the file's bytes; SVG is refused (script risk); max 1 MB. URLs carry `?v=<version>` so browsers cache them long and still refetch after an edit.
- **Frontend:** `BrandingEffect` sets the tab title, favicon and brand colour; `BrandMark` shows logo + name in the header, sidebar and login page; admin page `/app/branding`. Nothing hard-codes a name any more; the seeded default ("IT Institute LMS") is data in the migration.
- **Emails:** notification-service reads the name from identity (cached 60 s, falls back to `MAIL_FROM_NAME`).
- **Certificates:** will read name, logo and signatory from the same endpoint (certificate workflow phase).
