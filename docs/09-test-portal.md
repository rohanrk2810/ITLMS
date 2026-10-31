# Test portal: question types, secure mode, camera

How a test is set, taken and reviewed. The endpoints are in `04-api-reference.md`; this page explains the rules and their limits.

## Question types

| Type | Answered by | Marked by |
|---|---|---|
| Single choice, Multiple select, True/False | ticking options | the option key (all or nothing) |
| Short answer | typing | the trainer's accepted answers, ignoring capitals and extra spaces |
| Coding | writing code in the editor | running it against the trainer's test cases |

**Coding.** A test case is an input, the output a correct program prints, a weight and a *hidden* flag. Marks are the share of case weight the program passes. **Run tests** runs every case, hidden ones included; a hidden case shows the student only pass or fail. Output is compared ignoring line endings, trailing spaces and trailing blank lines. Marks come from the verdict stored beside a hash of the code, so a time-out or a trainer closing the test never has to run anything; submitting re-runs the tests for code that changed. If the runner is busy at that moment the earlier verdict stands instead of scoring zero. Running needs Piston or Judge0 (see `08-code-execution.md`).

## Secure test mode (`secureMode`, `maxViolations`)

Per test. The student sees the rules and clicks to begin (browsers only allow fullscreen from a click); the test then runs fullscreen with copy, cut, paste, right-click, print, save, view-source and developer-tool shortcuts blocked. The code editor keeps its clipboard, because writing code needs it; pasting is recorded.

Leaving the test window (another tab, another window, minimising) is a **counted violation**. Below `maxViolations` the student gets *"Warning: Please do not leave the test window."*; the violation that reaches the limit **terminates** the attempt: it ends at once, is scored on what was answered for the record, and is marked **failed** whatever the score, so progress and certificates never see it as a pass. `maxViolations` is set per test on the *New test* form and now **defaults to 1**: the first leaving fails the test at once. A value of 2 gives one warning, then termination.

**A terminated attempt can be retaken.** It does not count against `attemptsAllowed` (`AttemptServiceImpl.start` counts attempts that are not `TERMINATED`; the student's test list does the same), so the student must be able to sit the test again. It stays on record as failed.

Recorded but never counted: leaving fullscreen (an accidental Esc must not fail anyone; the student is asked to go back), copy and paste attempts, right-clicks, blocked shortcuts. The same leaving reported twice (tab hidden, then window blurred) is counted once.

## Camera (`requireCamera`)

Per test, independent of secure mode. Before starting, the student allows the camera and puts their face inside an **oval** drawn over their own picture. The browser then takes three samples of the face (see *Same face* below); **Start** stays disabled until the face is inside the oval and recorded. During the test a small live picture with the oval sits in the corner and the browser watches for:

| What | When it is reported | Event |
|---|---|---|
| No face | about 3 seconds | `FACE_NOT_DETECTED` |
| Face outside the oval | at the first check (about 1 s), again every 10 s | `FACE_OUT_OF_FRAME` (V9) |
| More than one face | about 3 seconds | `MULTIPLE_FACES` |
| Another person's body in view | two looks in a row (about 2 s) | `MULTIPLE_FACES` |
| A different face from the one at the start | two checks in a row (about 4 s), again every 20 s | `FACE_MISMATCH` (V11) |
| A mobile phone | the first time it is seen, again every 10 s while it stays | `DEVICE_DETECTED` (V11) |
| Camera switched off, or its permission removed | at once | `CAMERA_DISABLED`, `CAMERA_PERMISSION_DENIED` |

Each one is recorded with its time and shows the student a warning (for example *"Warning: Keep your face inside the oval."*). **None of them ends the attempt**: a detector in a browser has false alarms, and failing someone on its word alone would be worse than the problem. If the camera goes off a screen asks the student to turn it back on; the clock keeps running.

**No pictures are stored or sent.** Only the events and their times are, and only the trainers of the test and staff can read them.

### Choosing what is detected (`cameraChecks`)
When making a test with **Require camera** ticked, the trainer picks which detections run (all four are on by default):

| Check | What it looks for | Events it produces |
|---|---|---|
| `FACE` (Face in view) | a face in view and inside the oval | `FACE_NOT_DETECTED`, `FACE_OUT_OF_FRAME` |
| `PEOPLE` (Only one person) | a second face or another person | `MULTIPLE_FACES` |
| `IDENTITY` (Same face) | the face stays the one recorded at the start | `FACE_MISMATCH` |
| `PHONE` (Phone) | a mobile phone in view | `DEVICE_DETECTED` |

The camera staying on (and its permission) is always required, whatever is ticked. A check that is off does not run in the student's browser (its model is not even loaded: no phone/people model without `PHONE` and `PEOPLE`, no face recording without `IDENTITY`), and the server drops a report of it if one arrives anyway. The student still shows a face to start. Stored on `quizzes.camera_checks` (V13); tests from before keep all four. A test is a draft until published, and only a draft can be edited.

### The oval
`face-detector.ts` (`OVAL`): the oval is 60% of the picture wide and 84% tall. The face passes when the centre of its box is inside the oval and its corners are not more than 20% outside it. The picture is shown 4:3 so what is drawn is exactly what is measured.

### Same face
At the start, `CameraCheck` takes three samples about a second apart while one face sits in the oval. Each sample is a **128-number description of the face** (not a picture), made by face-api's recognition network. The three must agree with each other (distance at most 0.45) or they are retaken (*hold still*). During the test, every 2 seconds, when exactly one face is in view, the current face is compared with the closest of the three; a distance above **0.6** twice in a row raises `FACE_MISMATCH`. The numbers live in the page's memory only: never on the server, never in storage. **If the page is reloaded the student goes through the camera check again and a new reference is taken**, so a reload is a point where someone else could sit down.

### Phone and another person
The browser also runs an object detector (COCO classes `cell phone` and `person`) every second. A phone scoring 0.3 or more is reported the first time it is seen. The threshold was chosen on real photographs: clear phones score 0.5 to 0.75, small or tilted ones 0.3 to 0.45, and on 66 photographs of people at desks with no phone nothing scored above 0.21. A person needs 0.55 and two looks in a row, because a poster or photo can look like one. The camera check shows *Phone and second-person check: ready* (or says it could not start in this browser).

### Models and where they come from
All of it runs in the student's browser and is served by this app, not a CDN. Fetched only for camera tests (about 11 MB in all, so the first camera check takes a few seconds longer).

| Purpose | Model / library | Where it lives |
|---|---|---|
| Faces, oval | MediaPipe BlazeFace short-range (0.2 MB) + WebAssembly runtime | `frontend/public/models/blaze_face_short_range.tflite`; the runtime is copied from npm into `public/mediapipe/` by `scripts/copy-mediapipe.mjs` before every build (about 33 MB, git-ignored) |
| Phone, other person | MediaPipe ObjectDetector, EfficientDet-Lite0 int8 (4.6 MB) | `frontend/public/models/efficientdet_lite0.tflite` (committed) |
| Same face | `@vladmandic/face-api` (TensorFlow.js): tiny face detector, 68-point tiny landmarks, face-recognition network (6.5 MB) | copied from the npm package into `public/models/face-api/` by the same script (git-ignored) |

If a model cannot load the others carry on: without the recogniser the test goes ahead without the same-face check, without the object detector there is no phone check, and the camera stays on either way.

## When a test can be taken (`availableFrom` / `availableUntil`)
The trainer chooses when making the test:

- **Any time**: no times. Open from the moment it is published until the trainer closes it.
- **At a fixed time**: an opening and a closing time. A student can start it only between them. After the closing time it has **expired** and cannot be started (*"This test is not open right now."*); the student's tests list says *Closed ... expired, you did not take it*. A sitting that is already running ends at the closing time too, even if its duration is not up (a test that closes at 11:00 closes at 11:00 for someone who started at 10:50 on a 30-minute paper).

The server decides all of it (`Quiz.isOpenAt`, and the deadline in `AttemptServiceImpl.start`). The times are chosen in the trainer's own time zone and stored as instants.

## Typing replay (`recordTyping`)
Optional per test, for **coding questions**. The student's browser records every edit to the code answer with its time and sends it in batches while they type; the trainer replays it afterwards from the results table (**Typing -> Replay**): play, pause, speed 1x to 16x, a slider, long pauses cut to 1.5 s.

- What is recorded: `[time_ms, offset, removed, inserted_text, pasted]` per edit (Monaco's change events); a paste is the editor's own paste event (`onDidPaste`). The starting text of each session is recorded too, so a resumed answer replays from where it stood. Nothing but the code is recorded.
- What the trainer sees: edits, time from first to last, characters typed and pasted, the pasted blocks of 40 characters or more (red marks on the slider; click to jump), a warning when most of the answer was pasted, and whether the replay ends with the submitted answer.
- Students are told on the rules screen before they start.
- Stored in `quiz_typing_chunks` (V14), one row per batch (`seq` per question; a batch sent twice is kept once). Capped at 3,000,000 characters per attempt and 20,000 edits per answer. Accepted and ignored when the test does not record typing, the question is not a coding question of it, or the attempt is over.
- API: `POST /api/quiz-attempts/{id}/questions/{questionId}/typing` (student), `GET` the same path (trainers of the test and staff).
- It is what the student's browser reports, so it can be switched off or faked: a hint to look closer, not proof. A coding answer with no typing at all is worth a look too.

## How a test is shown
- **One question at a time.** A question map at the top (green = answered, outlined = on screen) jumps to any question; **Previous** and **Next** are at the bottom, and **Submit test** is always there. Moving between questions keeps every answer.
- **Coding questions** get a wide two-pane workspace: the problem and its examples on the left (with the database for SQL), the editor on the right, and **Testcase** / **Test Result** tabs under it. **Run** (or Ctrl+Enter) runs every case, hidden ones included, and opens the Test Result tab. The two dividers can be dragged. Other question types stay in a narrow card.
- **Full screen** for the coding workspace: the panel is laid over the page (not the browser's fullscreen, which a secure test already uses) with the clock in its toolbar; **Esc** leaves it. The test's warnings and the camera picture stay on top of it. The practice editor in a lesson and the trainer's code tab in a live class have the same button.

## What this can and cannot do

- **The browser does the watching.** A determined student can block the reports, use a second device, or photograph the screen. The value is as a deterrent, an honest record, and the trainer noticing a sitting that reports nothing at all. Do not describe it as proof of honesty.
- **The camera needs a secure origin.** Browsers only give a page the camera on `https://` or `http://localhost`. A LAN deployment on plain `http://` needs TLS in front of the frontend.
- **Face detection is short-range.** Tested against a real photograph through the same code path: a face about a quarter of the picture wide is found with high confidence; a small or distant face is ignored, and a second person sitting well behind the first can be missed. It is tuned to warn rather than to be certain.
- **Same face is an estimate.** Poor light, glasses on or off, or a turned head can push the distance past 0.6 for the right person, and a photograph held up to the camera is accepted as the same face (there is no liveness check). The threshold `SAME_FACE_MAX_DISTANCE` in `face-identity.ts` is the one to tune after trying it on real webcams.
- **A phone is found only when it is in the picture.** One in a pocket, on the desk below the frame, or held for less than a second between two looks is not seen. Another device out of the picture cannot be seen at all.
- **If the detector cannot load** (an old browser, WebAssembly blocked) the camera is still required and shown, the student may start, and only the face check is unavailable.

## Reviewing

In the test's results, each attempt of a secure or camera test has a **Monitoring** cell: the number of counted violations and of camera events, and a *review* button listing every event, oldest first, with the time and whether it counted. A terminated attempt shows **Terminated** instead of Passed.

## Trying it by hand

1. As a trainer, create a test with *Secure test mode* and *Require camera*; add a coding question with test cases; publish.
2. As an enrolled student, open it: read the rules, allow the camera, wait for "Your face is visible", start.
3. Switch to another tab: the warning. Do it again: the attempt ends as failed.
4. Cover the camera for a few seconds: the face warning; look at the trainer's review for the events.

## Creating a test from a JSON file or a pasted ChatGPT reply

The *New test* dialog has **Create from a JSON file** and a box to paste ChatGPT's reply. Both read the question-bank JSON format (`{"questions": [...]}`, see `question-bank-example.json` in liveclass-service); `title`, `instructions`, `durationMinutes`, `passPercentage` and `attemptsAllowed` may sit beside `questions` and fill the form. Text around the JSON, such as a markdown code fence, is cut away. When the test is created, the questions are added to the draft one by one. Supported types: MCQ, MULTIPLE_SELECT, TRUE_FALSE, SHORT_ANSWER and CODING (with test cases); open questions cannot be marked and are left out with a warning. The reader is `frontend/src/lib/test-json.ts`. Dialogs now scroll when taller than the screen.

## Making a question bank with ChatGPT (2026-10-31)

On the **Question bank** page the box *Make questions with ChatGPT* has these steps: **Download prompt file** saves the whole instruction text (the format, the rules and an example, with the "describe it here" line replaced by a note that the request is written in the chat) as `question-bank-prompt.txt`; attach it in ChatGPT and, in the same message, say the topic and how many questions (and kinds, difficulty, folder if wanted). Ask for the answer as a `.json` file and press **Upload .json file**, or paste the reply in the box and press *Add these questions to my bank*. **Copy ChatGPT prompt** puts the same text on the clipboard instead. Excel and CSV templates and *Upload filled file* are unchanged.

Questions can carry tables (`tables`: up to 20, each up to 60 columns and 20 000 rows) and SQL: `setupSql` (Oracle `CREATE TABLE` and one `INSERT` per row) builds the database before the student's query, and `testCases[].expectedOutput` is what the correct query prints (one row per line, columns joined by `|`, no heading, an empty value for NULL). The data in `tables` and in `setupSql` must be the same, or the student sees one thing and the query runs on another.

## Saving answers (2026-10-31)

The page autosaves every 20 seconds and sends all the answers; the server now writes only the ones that changed and loads the chosen options and the test cases for the whole paper in one query each, instead of one per question. A save went from about 26 database queries to about 12 and from a p95 of 604 ms to 89 ms with 150 students at once (local load test, [25-load-testing.md](25-load-testing.md)).

## Remote-control software (desktop app only, 2026-10-31)

In the SutraVidya desktop app a secure test also looks for remote-control programs on the computer (AnyDesk, TeamViewer, RustDesk, Getscreen, UltraViewer, Ammyy, Remote Utilities, LogMeIn, Chrome Remote Desktop, VNC, Splashtop, Supremo, DameWare, ScreenConnect, Windows Remote Assistance). Before the test starts, the *Start test* button stays off and the screen names the program until it is closed (checked every 5 seconds). During the test the app looks every 15 seconds; a program found is sent to the server as `REMOTE_TOOL` (the detail names it), the student sees *"Remote-control software is running on this computer. Close it now"*, and the trainer sees it in the violation list. It never ends the attempt: it is a match on program names, so it catches the common ones and not a renamed copy or one hidden on purpose. It is one layer next to the camera and window checks.

A **browser cannot look at the computer's programs**, so on the web this check does nothing. To rely on it, have students take secure tests in the desktop app (`desktop/preload.js` offers `sutraDesktop.remoteTools()`, `desktop/main.js` does the lookup with `tasklist` on Windows or `ps` elsewhere and answers only the app's own page). The new violation type needed migration V15 in assessment-service.

## Question pool (2026-10-31)

A test can hold more questions than a student should get. In the *New test* dialog, **Questions for each student** sets how many (empty = every question). When a student starts an attempt the server draws that many at random and keeps the draw on the attempt (`quiz_attempts.question_ids`, with `max_marks` for what they are worth together; V16), so:

- neighbours do not sit the same paper, and a reload or a second look shows the same questions;
- answers to a question the student was not given are refused (`not part of this test`), and the answer sheet, the result and the score all count only the drawn questions: the percentage is the score over that attempt's own `max_marks`;
- before starting, the student is shown what one draw is worth (`quizzes.attempt_marks`, the test's marks scaled by drawn/total, set on publish). If questions have unequal marks the draw can be worth a little more or less than that;
- publishing is refused when the number is larger than the questions in the test, and a pool as large as the test is dropped. A second attempt draws again. The test list shows "3 of 8 questions each".

## Pasted code on the results sheet (2026-10-31)

For tests that record typing, the server counts the characters typed and pasted in each coding answer as the edits arrive (`quiz_typing_stats`, V16; the starting snapshot counts as neither). The staff results sheet shows a red **Pasted code** note under the replay button when at least 80 characters were pasted and pasted text is 60% or more of what was inserted ("Pasted 330 of 333 characters of code (largest paste 330)"), so a trainer sees where to open the replay without opening every one. It is the browser's own report: a hint to look, not proof.

## Shuffled questions and answer options (2026-10-31)

The *New test* dialog has two switches, both on by default: **Shuffle the questions** (`shuffle_questions`) and **Shuffle the answer options** (`shuffle_options`, V17). With them on, every attempt sees the questions in its own order and the options of each single- or multiple-choice question in its own order, so a neighbour's "B" means nothing. The order is worked out from the attempt and question ids, so a reload shows the same order and another attempt shows another; nothing is stored. True/false keeps True before False. Marking uses option ids, never positions, so a right answer scores wherever it was shown, and the result lists the options in the order that student saw. The trainer's own view of the test keeps the order it was written in. Until 2026-10-31 only the question order was shuffled and there was no switch for it in the dialog.
