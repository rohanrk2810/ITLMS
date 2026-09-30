# Live class recording and content protection

How a class is recorded and what deters a student from casually copying it. The endpoints are in `04-api-reference.md`;
this page explains the design, what it needs to actually work, and - the point of this page - what it does not do.

## Recording

A class trainer or staff can start and stop a recording while a class is live. It captures a **room composite**: every
participant's camera, screen share and microphone mixed into one file, the same view `VideoConference` shows. There is
no auto-record; a class is only ever recorded when someone deliberately turns it on.

```
POST /api/liveclass/sessions/{id}/recording/start   -> 202, starts an Egress capture
POST /api/liveclass/sessions/{id}/recording/stop    -> 202, asks it to finish (not instant)
GET  /api/liveclass/sessions/{id}/recording         -> the finished file, once there is one
```

**How it's wired.** Capturing is done by [LiveKit Egress](https://docs.livekit.io/home/egress/overview/), a separate
service (`livekit-egress` in `deploy/docker/docker-compose.yml`) that runs a headless Chromium to render the room and
`ffmpeg` to encode what it renders. `liveclass-service` never touches video itself - it asks Egress to start
(`LiveKitGateway.startRoomCompositeEgress`), Egress writes an `.mp4` to the `recordings` Docker volume (mounted at
`/recordings` in both containers, no cloud upload configured), and LiveKit relays Egress's status changes back as
webhooks the same way it relays room and participant events. `egress_ended` is what sets `LiveSession.recordingUrl`
once the file is confirmed complete (`LiveKitWebhookHandler.onEgressEnded`) - a capture that fails leaves the class
with no recording rather than a broken link. A class still recording when it ends is stopped as part of settling it
(`LiveAttendanceServiceImpl.settle`), alongside auto-closing any open question (`10-student-progress-report.md`
covers the rest of that path; see also `04-api-reference.md`'s live-class questions section).

**`GET .../recording` does not honour byte ranges.** It returns the whole file with one `Content-Type: video/mp4`
response. The frontend (`api/live-classes.ts#fetchRecording`) fetches it once via `axios` (so the request carries the
normal `Authorization` header, which a plain `<video src>` cannot send) and hands the browser an object URL; scrubbing
then works within what has downloaded, not while it is still arriving. Fine for a class recording; not how you would
serve a two-hour lecture at scale.

**Running Egress is the expensive part of this feature.** It is real-time video compositing plus encoding - LiveKit's
own guidance is roughly 4 vCPU / 4 GB RAM per concurrent recording, an order of magnitude more than every other
container in this stack. `livekit-egress` is written into `docker-compose.yml` but is **not started by the default
`docker compose up -d`** unless it is named explicitly; every other service, including live classes without
recording, runs fine without it. Starting a recording with no `livekit-egress` container running fails cleanly:
LiveKit waits for a worker to pick the job up over Redis, gets none, and the request times out as
`503 LIVEKIT_UNREACHABLE` - no half-started state is left on the class (verified in the E2E: starting a capture with
no worker running leaves `recording: false` and `recordingUrl: null` on the session).

## Content protection

Two deterrents, both in the frontend, both applied only to students watching (a host watching their own class has
nothing to be deterred from - `ContentProtection`'s `active` prop is `false` for them):

- **Blurred while the tab isn't in view.** `document.visibilitychange` and `window` `blur`/`focus` toggle a
  black/blur overlay over the video area. It does not pause playback - only covers it - so switching back shows the
  moment it actually reached, not where it was left off.
- **A watermark naming the viewer**, tiled faintly across the video (`ContentProtection`'s nine-cell grid), and a
  **PrintScreen key event** is caught and logged (`toast.warning`, a brief on-screen flash) since it is one specific
  shortcut a browser page can actually see.

### What this does not do, stated plainly

**Nothing served to a browser can be protected from the person it is served to, and no browser page can detect an
OS-level screenshot.** Windows' Snipping Tool, macOS's Cmd+Shift+3/4/5, and a phone camera pointed at a screen are all
invisible to page JavaScript - there is no event, no permission, no API that would tell this code any of them
happened. `ContentProtection`'s own doc comment says this outright, and it is worth repeating here because "content
protection" as a phrase oversells what follows: the tab-blur hides the frame from a second monitor or a
screen-recorder's preview window while attention is elsewhere; PrintScreen specifically (not the OS shortcuts above)
is logged because it is the one case this page can see; the watermark does not stop a copy, it makes one that gets
out identifiable to who made it. Anyone extending this should keep describing it in those terms rather than as
"screenshot blocking".
