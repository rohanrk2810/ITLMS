# 21 - Video lesson monitoring

For direct video lessons, the instructor can switch on a camera check, make the video pause when the student's face is missing, and see how much each student really watched. Lives in course-service (V4 migration) and the lesson player.

## It is opt-in, per lesson, and every option is its own switch
Set by an admin, coordinator or trainer in **Course editor** - when adding a VIDEO lesson, or later under "Camera monitoring for this video" on any existing lesson (each change saves at once). All off by default; existing lessons behave as before.

| Switch | Effect |
|---|---|
| Watch through the camera | The student must allow the camera before the video starts; a face-in-view check runs while it plays. |
| Pause when the face is missing | After N seconds (2-60, default 5) with no face the video pauses and cannot be restarted until the student is back. Needs the camera switch. |
| Resume by itself | When the face is back the video carries on. Off: the student presses **Resume** (enabled only once a face is seen). Needs the pause switch. |

A switch that cannot act yet is shown disabled, and turning a parent switch off turns its children off.

## A monitored video completes by being watched, not by a button
For a lesson with *Watch through the camera* on:
- **No "Mark as complete" button.** The student sees *Watch the video through to complete this lesson*. The lesson completes by itself once **90%** of the video has really been played (`COMPLETE_AT_PERCENT` in the player), or when a video file or YouTube video ends having reached that.
- **The server checks it too.** `ProgressServiceImpl` ignores a completion request for a monitored video unless its recorded watch figure is at least 90%, so a hand-made request cannot complete it. Where the length is unknown (no duration on a Google Drive or Vimeo link) nothing can be measured, so that case keeps the button.
- **The face must be inside an oval**, the same one as in tests: a face outside it counts as missing, and with *Pause when the face is missing* on the video pauses; for Drive and Vimeo links the time does not count and a banner asks the student to keep their face inside the oval.

## What is counted
- **Watched %** = seconds the video was actually *playing* / its length. Skipping ahead adds nothing, and paused time adds nothing. (The older `watched_seconds` - furthest position - is kept for "resume where you left off" but is not the percentage.)
- **Face away** = seconds with no face while the video was playing. **Auto-pauses** = times it paused for that.
- Sent every 15 s, on pause and on leaving, as `playedSeconds`, `durationSeconds`, `faceAwaySeconds`, `facePauses`. Each counter only moves forward and played time is capped at the video's length, so a repeated or stale request cannot lower or inflate it.
- The student sees "Watched so far: N%" under the video and on the course page. Staff open **Who watched** (per VIDEO lesson in the course editor, `GET /api/progress/lessons/{id}/watch-report`): every enrolled student, least-watched first, with watched %, played/length, face-away, auto-pauses and status (names come from the batch roster).

## Privacy and limits
- The face check runs in the student's browser (MediaPipe, like the test portal and live classes). No picture or video is stored or sent - only the counts above.
- **YouTube links** (YouTube's IFrame Player API) and **direct video files** (our own player): full support - real playing time, watched %, and the video pauses when the face is missing.
- **Google Drive and Vimeo links** play inside a frame we cannot control, so the video **cannot be paused** and its playing time is unknown. They are still watched through the camera: a banner asks the student to keep their face in view once it has been missing for the grace time, face-away time is recorded, and "watched" means *time in front of the camera while the page is on screen* - shown as a % of the lesson's duration when the lesson has one, otherwise as minutes. It cannot tell whether the student actually pressed play, so treat it as attendance at the screen, not proof of viewing. The "pause" and "resume" switches have no effect on these links.
- Counted in the browser, so a determined student can tamper with it. It records and pauses; it does not stop anyone from completing a lesson.
- If the face detector cannot load, the video still plays and is counted; only the face check is unavailable.
- A student with no working camera cannot watch a lesson the instructor chose to monitor.
- The watch report is open to all admin/coordinator/trainer accounts, not only the course's own trainer (same as the batch progress view).
