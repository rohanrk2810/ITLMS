# Public website

`/` is a public front page (`frontend/src/pages/home/`). A visitor who is not signed in sees the site; a signed-in visitor still goes straight to `/app`, so the Android and desktop apps behave as before.

- **Sections:** top bar with a **Login** and a **Register** button, hero, technology ticker, About, Courses, Why us, How it works, enquiry form, Contact, footer with the four policy links.
- **Real data only:** courses come from the public catalogue (`GET /api/courses/public`, published courses); name, tagline, address, phone and email come from branding. There are no invented figures, reviews or placement numbers. The text in `home-content.ts` (features, steps, about points) is general; replace it with the institute's own wording and add real numbers or testimonials once they exist.
- **Enquiry form:** posts to `POST /api/leads/enquiry` (public, rate-limited at the gateway) and lands in the admission team's leads list. A hidden honeypot field catches simple bots. No gateway or backend change was needed.
- **Text size:** like sign-in, the website ignores the portal's text-size setting (`useNormalTextSize`).
- **Motion:** sections fade in on scroll, colour blobs drift and the ticker scrolls; all of it is switched off for visitors who ask for reduced motion.
- A test (`home-page.test.tsx`) checks the page shows a Login button and the fallback name `SutraVidya` when the branding call fails.
