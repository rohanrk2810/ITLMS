# Live-class student monitoring

A camera-based check that a student's face stays visible during a live class. **Off unless an ADMIN (or the class's trainer, for that one class) turns it on.** Lives in liveclass-service (V5 migration) and the room page.

## Who decides, and at what level
- Settings exist at four levels: **institute**, **course**, **batch**, **class session** (the timetable session id). The most specific one that exists wins; no setting anywhere means OFF.
- An ADMIN may read and change settings at every level (`GET/PUT /api/liveclass/monitoring/settings`, `DELETE .../{id}`). A class's own trainer (or staff) may `PUT` only a **SESSION** setting for a class they teach, from the Monitoring tab of the live room; the service checks it, not only the annotation. Removing a setting makes that level inherit from the one above. Changes are audited (`MONITORING_SETTING_CHANGED/REMOVED`).
- Per setting: monitoring on/off, face-visibility check, camera required to join, warn-after seconds (3-300, default 10), show the student a warning, custom warning text (default "Please sit properly and keep your face visible."), record events.
- The course level is found through the batch (asked of batch-service once per batch). If that cannot be read, the course level is skipped and the others still apply.

## What the student sees
- Where monitoring is on, the student first gets a plain notice (camera used only to check the face is visible; no pictures or video stored; trainer and admin can see short notes) and is asked for the camera. Where monitoring is on the camera is **always required**: there is no way to join without it. (The old per-setting *camera required* switch was removed from the admin page; the column still exists in the database and is ignored. The microphone is still its own switch.)
- Where it is off, the room opens as before and **the camera is never requested for monitoring**.
- A face missing for the configured time shows a small amber banner, not a modal, so the class is not interrupted. It clears when the face returns. A live preview in the corner shows what the camera sees.
- The check runs in the browser (MediaPipe BlazeFace, the same detector as the test portal). If it cannot load, the camera stays on and only the face check is unavailable; a failed lookup of the setting never locks a student out.

## Events (no pictures, ever)
`FACE_NOT_DETECTED` (reported when the threshold is crossed, again at 60 s as CRITICAL), `FACE_RESTORED` (with how long), `MULTIPLE_FACES`, `CAMERA_DISABLED`, `CAMERA_PERMISSION_DENIED`. Each stores student, class, type, time, seconds into the class, duration and severity (INFO/WARNING/CRITICAL, decided on the server).
- Written by `POST /api/liveclass/class-sessions/{id}/monitoring/events`, STUDENT only, only for a class they joined, only while it is LIVE, only where monitoring is on (and face events only if face visibility is on), capped at 300 per student per class. Anything else is dropped or refused.
- Read by the class trainer and staff (`GET .../monitoring/events`; `HostAccess.requireHostOf`): a "Monitoring" tab in the host panel, refreshed every 10 s.

## Stricter options (V7) - each its own switch, all off by default
Shown as separate switches in the trainer's Monitoring tab (and as checkboxes on the admin page). Only available while the main monitoring switch is on.
| Switch | Effect |
|---|---|
| Face check | The original face-visibility check. |
| Tab-switch check | Notes `TAB_AWAY` (with how long) when the student leaves the class tab/window for 5 s or more; the student sees a notice on return. |
| Strict severity | A missing face and more than one face are CRITICAL at once; a tab left 30 s (not 60 s) is CRITICAL. |
| Attendance penalty | When attendance is settled, seconds with the face missing and the tab left are taken off the student's attended time before the PRESENT/LATE/ABSENT rule. Needs "record events" on. |
| Alert the trainer | A toast in the trainer's panel for every new CRITICAL note. |
| Auto-remove | After N CRITICAL notes (1-20, default 3, and again at each multiple) the server removes the student from the room and writes `REMOVED_BY_MONITORING`. They may rejoin. A failure to reach LiveKit never loses the notes. |

**Student summary:** `GET /api/liveclass/class-sessions/{id}/monitoring/report` gives one line per student (face absences and time, tab leaves and time, extra faces, camera problems, warnings/critical, removed, penalty). Shown under the Monitoring tab and on the class review page.

## Limits
- Tab-switch is browser-only: it cannot see a second device. A tab left and never returned to is reported only if the student comes back.
- The check runs on the student's machine, so a determined student can defeat it. It records and warns; it never removes anyone or fails anything by itself.
- Events cannot be reviewed after class from a page yet (only the live panel); the API serves them any time.
- No tenant column: isolation is per deployment (docs/13).
