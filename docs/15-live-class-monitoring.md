# Live-class student monitoring

A camera-based check that a student's face stays visible during a live class. **Off unless an ADMIN turns it on.** Lives in liveclass-service (V5 migration) and the room page.

## Who decides, and at what level
- Settings exist at four levels: **institute**, **course**, **batch**, **class session** (the timetable session id). The most specific one that exists wins; no setting anywhere means OFF.
- Only an ADMIN may read or change settings (`GET/PUT /api/liveclass/monitoring/settings`, `DELETE .../{id}`); the service checks it, not only the annotation. Removing a setting makes that level inherit from the one above. Changes are audited (`MONITORING_SETTING_CHANGED/REMOVED`).
- Per setting: monitoring on/off, face-visibility check, camera required to join, warn-after seconds (3-300, default 10), show the student a warning, custom warning text (default "Please sit properly and keep your face visible."), record events.
- The course level is found through the batch (asked of batch-service once per batch). If that cannot be read, the course level is skipped and the others still apply.

## What the student sees
- Where monitoring is on, the student first gets a plain notice (camera used only to check the face is visible; no pictures or video stored; trainer and admin can see short notes) and is asked for the camera. If "camera required" is set, they cannot join without allowing it; otherwise they may join without monitoring.
- Where it is off, the room opens as before and **the camera is never requested for monitoring**.
- A face missing for the configured time shows a small amber banner, not a modal, so the class is not interrupted. It clears when the face returns. A live preview in the corner shows what the camera sees.
- The check runs in the browser (MediaPipe BlazeFace, the same detector as the test portal). If it cannot load, the camera stays on and only the face check is unavailable; a failed lookup of the setting never locks a student out.

## Events (no pictures, ever)
`FACE_NOT_DETECTED` (reported when the threshold is crossed, again at 60 s as CRITICAL), `FACE_RESTORED` (with how long), `MULTIPLE_FACES`, `CAMERA_DISABLED`, `CAMERA_PERMISSION_DENIED`. Each stores student, class, type, time, seconds into the class, duration and severity (INFO/WARNING/CRITICAL, decided on the server).
- Written by `POST /api/liveclass/class-sessions/{id}/monitoring/events`, STUDENT only, only for a class they joined, only while it is LIVE, only where monitoring is on (and face events only if face visibility is on), capped at 300 per student per class. Anything else is dropped or refused.
- Read by the class trainer and staff (`GET .../monitoring/events`; `HostAccess.requireHostOf`): a "Monitoring" tab in the host panel, refreshed every 10 s.

## Limits
- The check runs on the student's machine, so a determined student can defeat it. It records and warns; it never removes anyone or fails anything by itself.
- Events cannot be reviewed after class from a page yet (only the live panel); the API serves them any time.
- No tenant column: isolation is per deployment (docs/13).
