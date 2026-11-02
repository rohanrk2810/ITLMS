# Identity cards

Students, trainers and other staff can be given a printed ID card. ADMIN and COORDINATOR issue and print them; the person can see and download their own. Built into certificate-service (it already draws PDFs with the institute's branding and has a public verification page).

## What a card shows

CR80 (credit-card size, 54 x 85.6 mm), upright: the institute logo and name in a coloured band, the photo, the name, the role (Student, Trainer, or a staff title), the person's code (student code or employee code), the batch for a student, the valid-until date, a QR code, and the card number such as `STU-2026-0042`. The prefix is `STU`, `TRN` or `STF`; the number comes from a database sequence.

The QR code opens the public check page `/verify-id/<card number>?code=<code>`. That page shows only the name, role, validity and whether the card is valid, expired or withdrawn.

## Who can do what

| Action | Who |
|---|---|
| Issue a card, add or change the photo, print a card or an A4 sheet, withdraw a card, list all cards | ADMIN, COORDINATOR |
| See and download their own card | the student or trainer it belongs to (matched by the login) |
| Check a card by number and code | anyone (public, rate limited like certificate checks) |

A staff card has no login attached, so it is printed by the office and is not shown on any "my card" page.

## Rules

- A student or trainer is chosen from admission-service (their id), so the name and code on the card are the ones on record. A student who has dropped out cannot be given a card. A staff card is typed in (name and title).
- **Valid until:** the date given, otherwise the end date of the batch printed on a student card, otherwise one year. It cannot be in the past.
- **One card in force per person.** Issuing a new card revokes the old one ("Replaced by a new card"). Withdrawing a card needs a reason and tells the person.
- A withdrawn or expired card cannot be printed. It still verifies, as WITHDRAWN or EXPIRED.
- **Photo:** JPEG or PNG up to 2 MB. The type is read from the file's first bytes, not from what the browser said. The photo is kept in its own table (`id_card_photos`) so a list of cards never loads pictures. With no photo the card shows the first letter of the name.
- **A4 sheet:** up to 60 cards, nine per page (three across, three down), for cutting. Cards that cannot be printed are skipped; if none can, nothing is produced.
- Issuing, photo changes, printing and withdrawing are written to the audit log.

## API (through the gateway, `/api/id-cards`)

| Method and path | Who | What |
|---|---|---|
| `POST /` | STAFF | Issue `{personType, personId, fullName, designation, batchId, validUntil}` |
| `GET /?type=&status=&q=` | STAFF | The newest 300 cards |
| `GET /me` | signed in | Own cards |
| `GET /{id}` | staff or owner | One card |
| `PUT /{id}/photo` | STAFF | Multipart `file` |
| `GET /{id}/photo` | staff or owner | The picture |
| `GET /{id}/pdf` | staff or owner | The card as a PDF |
| `GET /sheet?ids=1,2,3` | STAFF | Cards on A4 sheets |
| `POST /{id}/revoke` | STAFF | `{reason}` |
| `GET /verify/{cardNo}?code=` | public | Name, role, validity, status |

## Screens

- **ID cards** in the menu. Staff see the list with filters (type, search), "Issue a card", "Print selected" for a sheet, and per row Add photo, PDF and Withdraw. A student or trainer sees "My ID card" with a Download button.
- **/verify-id/...** is the public page the QR code opens.

## Settings

`ID_VERIFY_URL` (set in `deploy/docker/.env`, read by `config-repo/certificate-service.yml`) is the public address of the check page, for example `https://app.example.com/verify-id`. Set it, and `CERT_VERIFY_URL` for certificates, to the real address of the web app on any server, or the QR codes point at `localhost`. The card colour is a fixed orange; the logo and institute name come from the Branding page.

## Limits

- Text uses the PDF standard fonts, which cover Latin script only. A name in Devanagari or another script needs an embedded font in `IdCardPdfRenderer`.
- Only the front of the card is drawn. A back with an address or rules would be a second drawing.
- The list is capped at 300 cards; use the search to reach older ones.
- Drawn by `IdCardPdfRenderer` with the zxing core library (QR) and OpenPDF.

## Student photo and full registration details

- Registration (the public form) and the staff "Admit" dialog now require the full profile: date of birth, gender, education, address, PIN code and guardian. College, graduation year and emergency contact are optional. Self-registration carries the details on `UserCreatedEvent.studentDetails`; Google/WhatsApp sign-ups cannot ask, so those students fill them in on **My profile**.
- The student adds their own photo once on **My profile** (`PUT /api/students/me/photo`, JPEG or PNG up to 256 KB). After that only ADMIN or COORDINATOR can replace it (`PUT /api/students/{id}/photo`, "Change photo" on the student page). Stored in admission-service `student_photos` (migration V2).
- Issuing a student ID card copies that photo onto the card, so nobody uploads it twice. A card keeps the photo it was issued with; reissue the card after staff change the photo.
