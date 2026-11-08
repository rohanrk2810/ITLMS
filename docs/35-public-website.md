# Public website

`/` is a public front page (`frontend/src/pages/home/`). A visitor who is not signed in sees the site; a signed-in visitor still goes straight to `/app`, so the Android and desktop apps behave as before.

- **Sections:** top bar with a **Login** and a **Register** button, hero, technology ticker, About, Courses, Why us, How it works, enquiry form, Contact, footer with the four policy links.
- **Real data only:** courses come from the public catalogue (`GET /api/courses/public`, published courses); name, tagline, address, phone and email come from branding. There are no invented figures, reviews or placement numbers. The built-in wording in `home-content.ts` is general; the admin replaces it from the screen below.
- **Enquiry form:** posts to `POST /api/leads/enquiry` (public, rate-limited at the gateway) and lands in the admission team's leads list. A hidden honeypot field catches simple bots. No gateway or backend change was needed.
- **Text size:** like sign-in, the website ignores the portal's text-size setting (`useNormalTextSize`).
- **Motion:** sections fade in on scroll, colour blobs drift and the ticker scrolls; all of it is switched off for visitors who ask for reduced motion.
- A test (`home-page.test.tsx`) checks the page shows a Login button and the fallback name `SutraVidya` when the branding call fails.

## Editing it (Customize > Website)

The administrator edits the page without a deploy: the label, headline and sentence at the top, up to 4 figures, the About points, up to 9 "Why us" cards, up to 6 steps, up to 6 student feedback entries, and which sections are shown. Icons are picked automatically in order. Open the website from that tab (`/?preview=1`) to check the result while signed in; without `preview`, a signed-in person is sent to the app.

- Stored under `site` in the portal customization JSON (`ui_config`), so there is no new table or endpoint. The server accepts at most 20,000 characters for the whole JSON, shared with the portal settings; the list limits above keep the website well inside it, and the tab shows how much is used and blocks saving past the limit.
- A box left empty, or a list never edited, keeps the built-in wording. With no feedback entries the feedback section is not shown. "Reset everything" on the Portal tab leaves the website text alone.
- Feedback and figures are whatever the admin types, so they must be real and, for feedback, given with the student's permission.
