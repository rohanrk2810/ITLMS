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

Leaving the test window (another tab, another window, minimising) is a **counted violation**. Below `maxViolations` the student gets *"Warning: Please do not leave the test window."*; the violation that reaches the limit **terminates** the attempt: it ends at once, is scored on what was answered for the record, and is marked **failed** whatever the score, so progress and certificates never see it as a pass. `maxViolations` 2 (default) means one warning, then termination; 1 terminates at once.

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
