# Legal pages and consent

Public pages `/privacy`, `/terms`, `/cookies`, `/refund` (frontend `src/pages/legal/`). The text lives in `legal-content.ts`; the institute name, address, email and phone come from branding. Links show under every sign-in and register page (`LegalLinks`).

- **Cookie notice** (`cookie-banner.tsx`): the site uses only essential storage (sign-in, captcha, theme), so it is one "Got it" notice, remembered in `localStorage`. If marketing or analytics cookies are ever added, this must become a real opt-in.
- **Register consent**: the Create account button stays disabled until the Terms and Privacy checkbox is ticked. The tick is not stored on the server yet; add a consent timestamp column (new Flyway migration) if proof of consent is needed.
- The texts are plain-language starting points (DPDP Act 2023, India), not legal advice. The institute must review them, especially refund rules and retention periods.
- The cookie notice is a slim bar at the bottom and is rendered inside the router (it contains links). `cookie-banner.test.tsx` guards that.
