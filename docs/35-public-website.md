# Public website

`/` is a public front page (`frontend/src/pages/home/`). A visitor who is not signed in sees the site; a signed-in visitor still goes straight to `/app`, so the Android and desktop apps behave as before. An administrator checks edits at `/?preview=1`, which stays on the page while signed in.

## What is on the page

Announcement bar (optional), top bar with **Login** and **Register**, hero (with an optional picture beside the words), technology ticker, About, Courses, Why us, How it works, photo gallery, trainers, hiring partners, student feedback, questions and answers, enquiry form, contact (with an optional Google map), footer with social links and the four policy links, and an optional floating WhatsApp button. A section with nothing in it (no photos, no trainers, no feedback, no questions) is left off.

- **Real data only:** courses come from the public catalogue (`GET /api/courses/public`, published courses); name, tagline, address, phone and email come from branding. Nothing is invented: no figures, reviews or placement numbers unless the administrator types them.
- **Enquiry form:** posts to `POST /api/leads/enquiry` (public, rate-limited at the gateway) and lands in the admission team's leads list. A hidden honeypot field catches simple bots.
- **English only by default.** The English/Marathi button is off until the administrator ticks "Language button" in Customize > Website. When it is on, every fixed word and default text exists in both languages (`home-content.ts`), the visitor's choice is remembered in the browser, and the first visit follows the browser's language. When it is off, the site is English whatever the browser's language.
- **Text size:** like sign-in, the website ignores the portal's text-size setting (`useNormalTextSize`).
- **Motion:** sections fade in, colour blobs drift and the ticker scrolls; all of it is off for visitors who ask for reduced motion.

## Editing it (Customize > Website)

Everything above is edited by the administrator without a deploy. With the language button on, the tab shows a switch (English or मराठी): texts are written once per language, and the other language is shown as a hint. With it off, only English is written. Pictures, links and numbers are the same in both. Anything left empty shows the standard wording; a list that was never edited shows the standard list.

| Part | What can be set |
|---|---|
| Announcement bar | on or off, text, link |
| Top of the page | label, headline, sentence, hero picture |
| Figures | up to 4 (value and label) |
| About | introduction and up to 6 points |
| Courses | show duration, show fee, and a picture for each course |
| Why us, How it works | up to 9 cards, up to 6 steps (icons are picked in order) |
| Gallery | up to 18 photos with captions |
| Trainers | up to 12: photo, name, role |
| Hiring partners | up to 18 logos (or names), only companies that agree to be shown |
| Student feedback | up to 6: name, photo, course or job, words, only with the student's permission |
| Questions and answers | up to 14 |
| Links | WhatsApp number, Instagram, YouTube, Facebook, LinkedIn, the floating WhatsApp button and its first message, a Google Maps embed address |
| Sections shown | untick to leave a section out |

## How it is stored

- The wording is one JSON document in `institute_settings.site_config` (migration V14), read anonymously through `GET /api/public/branding/site` and saved with `PUT /api/branding/site-config` (administrator only, at most 60,000 characters). It is separate from `ui_config` because every page load downloads that one.
- Pictures are rows in `site_images` (V14), served by id from `/api/public/branding/site-images/{id}` and cached for a month because the bytes never change under an id. The browser shrinks each picture before sending (about 1,600 pixels, less for logos and portraits); the server accepts PNG, JPEG or WebP up to 2 MB and refuses SVG, and holds at most 120 pictures.
- A picture taken off the page is deleted from the server only when the page is saved, so leaving the screen without saving never breaks the live page.
- Reading the JSON on the page never throws: unknown or malformed parts are ignored. Links must be web addresses or paths of this site (so stored text can never become a script), picture ids must be digits, and a map address must be a Google Maps embed address.

## Tests

`home-page.test.tsx` (the Login button, the fallback name `SutraVidya`, the administrator's wording, hidden sections, Marathi), `site-config.test.ts` (parsing, limits, unsafe links, picture ids), and the backend `SiteContentServiceTest` (saving, invalid JSON, picture type and limit, missing pictures).
