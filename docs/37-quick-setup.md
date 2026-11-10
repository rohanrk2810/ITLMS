# Quick setup: the institute and the website from one file

A new institute can give its details once, in one `.json` file, and have the whole website and every printed page follow. It works like the question bank: a prompt file for ChatGPT, an example, an upload, and a check before anything changes. **Customize > Quick setup** (ADMIN).

## The steps

1. **Download data sheet (PDF)**: lists every detail the institute has to give, with what it is for, an example and a box to write the answer. Static file `frontend/public/institute-data-sheet.pdf`.
2. **Download prompt file** (or **Copy ChatGPT prompt**): the rules, every field, a complete example, and an empty place for the institute's own facts. Attach it in ChatGPT and write or paste the details under it.
3. ChatGPT answers with a `.json` file. **Upload .json file**, or paste the reply into the box (a reply wrapped in a code block or in a sentence of chat is fine).
4. The file is checked and shown as a list of **what will change**, with errors (the file cannot be applied) and warnings (shortened, ignored, dropped). **Apply** asks once more.

## What the file holds

```json
{ "institute": { "name": "...", "tagline": "...", "primaryColor": "#2b0255", "contactEmail": "...", "contactPhone": "...",
                 "website": "...", "address": "...", "signatoryName": "...", "signatoryTitle": "..." },
  "website":   { "heroBadge": "...", "heroTitle": "...", "heroText": "...", "stats": [], "announcement": {}, "aboutIntro": "...",
                 "aboutPoints": [], "features": [], "steps": [], "trainers": [], "partners": [], "testimonials": [], "faqs": [],
                 "social": {}, "whatsappButton": true, "whatsappMessage": "...", "mapUrl": "...",
                 "showCourseFee": true, "showCourseDuration": true, "hiddenSections": [] } }
```

Every name and its limit is in one list, `frontend/src/lib/institute-setup-fields.json`. The same list makes the prompt, the example file, the checks and the PDF, so they cannot disagree. Add a field there, then run `python deploy/scripts/generate-setup-sheet.py` to rebuild the PDF.

## What happens on Apply

- Only the parts that were in the file change; **whatever it leaves out stays as it is**. The institute part goes through the same update as the Institute tab (`PUT /api/branding`, the saved values filled in for the fields left out), the website part through the Website tab save (`PUT /api/branding/site-config`). Nothing new on the server.
- A list in the file **replaces** that list on the website. A trainer, partner or student whose name stays the same (any capitals) **keeps the photo** already added; a new one has none until a photo is added on the Website tab.
- Pictures are not in the file: the logo, the favicon, the gallery and the photos are added by hand. The gallery is never touched.
- The checks: a main colour must be `#rrggbb`, the email must look like one, text over the limit is shortened (with a warning), links must be web addresses, only Google Maps addresses are kept for the map, only real section names can be hidden, and a list says how many items were kept (`14 of 21`). A name the file does not know is ignored with a warning.

## Not covered

The navbar and footer links, and the text of the blog, career guide and free-resource pages, are not in the file yet (see [35-public-website.md](35-public-website.md)).

Tests: `institute-setup.test.ts` (the example passes its own checks, the prompt, reading a reply, each check, merging) and `setup-tab.test.tsx` (the screen, apply only after agreeing, a file with an error cannot be applied).
