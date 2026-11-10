# Public website

`/` is a public front page (`frontend/src/pages/home/`). A visitor who is not signed in sees the site; a signed-in visitor still goes straight to `/app`, so the Android and desktop apps behave as before. An administrator checks edits at `/?preview=1`, which stays on the page while signed in.

## What is on the page

Announcement bar (optional), top bar with **Login** and **Register**, hero (with an optional picture beside the words), technology ticker, About, Courses, Why us, How it works, photo gallery, trainers, hiring partners, student feedback, questions and answers, enquiry form, contact (with an optional Google map), footer with social links and the four policy links, and an optional floating WhatsApp button. A section with nothing in it (no photos, no trainers, no feedback, no questions) is left off.

- **Real data only:** courses come from the public catalogue (`GET /api/courses/public`, published courses); name, tagline, address, phone and email come from branding. Nothing is invented: no figures, reviews or placement numbers unless the administrator types them.
- **Enquiry form:** posts to `POST /api/leads/enquiry` (public, rate-limited at the gateway) and lands in the admission team's leads list. A hidden honeypot field catches simple bots.
- **English only.** The site has no language switch. (An English/Marathi switch was built and then removed; content saved while it existed still reads, keeping the English.)
- **Text size:** like sign-in, the website ignores the portal's text-size setting (`useNormalTextSize`).
- **Login and Register open as a popup** over the page the visitor is on (`components/auth-dialog.tsx`, opened through `stores/auth-dialog-store.ts` by the top bar and by Buy on a course card). The forms are the ones the `/login` and `/register` pages show (`LoginForm`, `RegisterForm`), with the same captcha, Google and WhatsApp sign-in and checks; the two pages still exist for password-reset emails, the mobile app and bookmarks. The popup loads only when it is first opened. After signing in: a student on a public page such as /courses stays there, so the course they were about to buy is still in front of them, now with **Pay online**; staff, anyone with extra duties, and anyone signing in from the front page go to the portal. A new account goes to **My profile** first, to add the photo. "Forgot password?" leaves for its page and closes the popup.
- **Motion:** sections fade in, colour blobs drift and the ticker scrolls; all of it is off for visitors who ask for reduced motion.

## Editing it (Customize > Website)

Everything above is edited by the administrator without a deploy. Anything left empty shows the standard wording; a list that was never edited shows the standard list.

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
| Links | WhatsApp number, Instagram, YouTube, Facebook, LinkedIn, the floating WhatsApp button and its first message, a Google Maps link (see below) |
| Sections shown | untick to leave a section out |

## How it is stored

- The wording is one JSON document in `institute_settings.site_config` (migration V14); texts are plain strings, read anonymously through `GET /api/public/branding/site` and saved with `PUT /api/branding/site-config` (administrator only, at most 60,000 characters). It is separate from `ui_config` because every page load downloads that one.
- Pictures are rows in `site_images` (V14), served by id from `/api/public/branding/site-images/{id}` and cached for a month because the bytes never change under an id. The browser shrinks each picture before sending (about 1,600 pixels, less for logos and portraits); the server accepts PNG, JPEG or WebP up to 2 MB and refuses SVG, and holds at most 120 pictures.
- A picture taken off the page is deleted from the server only when the page is saved, so leaving the screen without saving never breaks the live page.
- **Map:** paste the link from Google Maps > Share (or the whole `<iframe>` from Share > Embed a map; only its address is kept). A share link such as `maps.app.goo.gl/...` cannot be embedded, so the map is found from the address in the Institute tab and an "Open in Google Maps" button opens the exact link; an embed address is shown as it is, for an exact pin. Only Google Maps addresses are accepted.
- Reading the JSON on the page never throws: unknown or malformed parts are ignored. Links must be web addresses or paths of this site (so stored text can never become a script), picture ids must be digits, and a map address must be a Google Maps address.

## Footer, the /courses page and Buy / Inquiry

- **Top bar** (`components/site-nav.tsx`, the same on the front page and on every public page): Home, Courses, Programs, Pricing, About and Contact; a **Resources** menu (Blog, Career Guide, Learning Paths, Free Resources, Success Stories, Student Community, FAQ); the institute phone number from Branding (large screens, a tap-to-call link); an **Inquiry** button that opens the enquiry form; the theme button; Login and Register, or **Open my portal** when signed in. The page you are on is underlined, the bar gains a shadow and shrinks a little once the page scrolls, and on a phone everything moves into a menu button whose panel slides down. Change the links in `LINKS` and `RESOURCES` at the top of the file.
- **Motion:** a page fades up as it opens, the cards of a grid follow one after another (`stagger`), link underlines grow on hover, footer links and social icons lift, buttons press in slightly, and menus slide. It is all CSS (`index.css`) and Tailwind classes, and is switched off for visitors whose system asks for reduced motion.
- **Footer** (`components/site-footer.tsx`, on the front page and on /courses): the institute logo, name and tagline from Branding; social icons for the Facebook, LinkedIn, YouTube, Instagram and WhatsApp links set under Customize > Website (an icon appears only when its link is set); three link columns (Explore, Resources, Company); a **Stay Updated** box; the four policy links.
- **Every footer link opens its own page of the app**, none leaves the site (routes in `App.tsx`, pages in `pages/courses/public-courses-page.tsx` and `pages/site/site-pages.tsx`, shared frame in `components/site-shell.tsx`):

| Link | Page | What it shows |
|---|---|---|
| Courses | /courses | every published course, search and level filter, Buy and Inquiry |
| Programs | /programs | the courses grouped by level |
| Pricing | /pricing | a fee table with Buy and Inquiry, how to pay, receipts and refunds |
| Blog | /blog | three short starter articles |
| Success Stories | /success-stories | the feedback added under Customize > Website |
| Learning Paths | /learning-paths | beginner, intermediate, advanced, with the courses at each step |
| Career Guide | /career-guide | six steps from learning to a first job |
| Student Community | /community | a WhatsApp button when the WhatsApp number is set, otherwise a link to Contact |
| Free Resources | /free-resources | seven independent learning sites, opening in a new tab |
| FAQ | /faq | the questions written under Customize > Website, or a default set |
| About Us | /about | the About text, points, features and trainers from Customize > Website |
| Contact Us | /contact | phone, email, address and map from Branding, and the enquiry form |
| Become an Instructor, Careers, Affiliates | /become-an-instructor, /careers, /affiliates | a short explanation and the enquiry form with the first line of the message already written |

- The blog articles, career guide, free-resource list, level descriptions and default FAQ are fixed general text in `pages/site/site-page-content.ts` (no claim about the institute's results). The administrator can change the FAQ, success stories, About text, trainers, contact details and WhatsApp link from the admin screens; the other text is changed in that file.
- **Stay Updated** takes an email and opens the enquiry form with it filled in, because an enquiry needs a name and a phone number to call back. There is no mailing list.
- **/courses** (`pages/courses/public-courses-page.tsx`) is open to anyone: every published course (up to 100) with its fee, a search box, a level filter, and the **Buy** and **Inquiry** buttons ([34-online-payments.md](34-online-payments.md)). The page's own course photos are the ones chosen under Customize > Website.

## Tests

`auth-dialog.test.tsx` (the popup, switching between sign in and register, where each kind of person lands), `public-courses-page.test.tsx` (the list, search, Buy and Inquiry buttons, footer links staying in the app), `site-pages.test.tsx` (each of the fourteen pages opens with its heading and the footer, levels and fees, the prefilled enquiry message, the default FAQ, external links in a new tab), `home-page.test.tsx` (the Login button, the fallback name `SutraVidya`, the administrator's wording, hidden sections, no language button), `site-config.test.ts` (parsing, content saved in the older two-language form, limits, unsafe links, picture ids, the map address), and the backend `SiteContentServiceTest` (saving, invalid JSON, picture type and limit, missing pictures).
