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

Per test, independent of secure mode. Before starting, the student must allow the camera and show a face; **Start** stays disabled until then. During the test a small live picture sits in the corner and the browser watches for: no face for about 3 seconds, more than one face, the camera switched off, its permission removed. Each one is recorded with its time and shows the student a warning (*"Warning: Please keep your face properly visible in the camera."* for a missing face). **None of them ends the attempt**: a face detector has false alarms, and failing someone on its word alone would be worse than the problem. If the camera goes off a screen asks the student to turn it back on; the clock keeps running.

**No pictures are stored.** Only the events and their times are, and only the trainers of the test and staff can read them.

Face detection runs in the student's browser (MediaPipe BlazeFace, short-range model, about 0.2 MB, plus a WebAssembly runtime). Both are served by this app, not a CDN: the model is committed under `frontend/public/models/`, and the runtime is copied from the npm package into `public/mediapipe/` by `scripts/copy-mediapipe.mjs` before every build (about 33 MB, ignored by git).

## What this can and cannot do

- **The browser does the watching.** A determined student can block the reports, use a second device, or photograph the screen. The value is as a deterrent, an honest record, and the trainer noticing a sitting that reports nothing at all. Do not describe it as proof of honesty.
- **The camera needs a secure origin.** Browsers only give a page the camera on `https://` or `http://localhost`. A LAN deployment on plain `http://` needs TLS in front of the frontend.
- **Face detection is short-range.** Tested against a real photograph through the same code path: a face about a quarter of the picture wide is found with high confidence; a small or distant face is ignored, and a second person sitting well behind the first can be missed. It is tuned to warn rather than to be certain.
- **If the detector cannot load** (an old browser, WebAssembly blocked) the camera is still required and shown, the student may start, and only the face check is unavailable.

## Reviewing

In the test's results, each attempt of a secure or camera test has a **Monitoring** cell: the number of counted violations and of camera events, and a *review* button listing every event, oldest first, with the time and whether it counted. A terminated attempt shows **Terminated** instead of Passed.

## Trying it by hand

1. As a trainer, create a test with *Secure test mode* and *Require camera*; add a coding question with test cases; publish.
2. As an enrolled student, open it: read the rules, allow the camera, wait for "Your face is visible", start.
3. Switch to another tab: the warning. Do it again: the attempt ends as failed.
4. Cover the camera for a few seconds: the face warning; look at the trainer's review for the events.
