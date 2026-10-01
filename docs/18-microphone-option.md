# Microphone option (tests and live classes)

Like the camera, the microphone is an option the author (tests) or the administrator (live classes) switches on. **Off by default.** No audio is ever recorded or stored, by either use.

## Tests (assessment-service V7)
`quizzes.require_microphone`, set with a checkbox when creating a test.
- Before starting, the student allows the microphone; the rules screen shows a live level bar and listens to the room for two seconds to learn its background noise. The loudness that counts as "sound" is derived from that (3 x background + a floor), so a noisy room does not flag constantly.
- During the test the browser reports `MICROPHONE_DISABLED` (switched off or stopped), `MICROPHONE_PERMISSION_DENIED` (permission removed), and `SPEECH_DETECTED` (sound above the threshold for 3 seconds in a row, then quiet about it for 30 seconds). Each warns the student and is kept with its time for the trainer, in the same violations list as camera events.
- Never ends an attempt and is never counted toward the violation limit: a level meter cannot tell speech from a television, a cough or a passing lorry. Only loudness is measured; the server stores the event type, time and a short note.
- The server accepts these events only for a test that requires the microphone (a microphone report on a camera-only test is ignored), repeated like the other monitoring events.

## Live classes (liveclass-service V6)
`monitoring_settings.microphone_required`, set in the admin Monitoring page at any level (most specific wins, like the rest).
- The student must allow the microphone to join (together with the camera). A note (`MICROPHONE_DISABLED` / `MICROPHONE_PERMISSION_DENIED`) is kept if it is later turned off or blocked, and the student sees a small banner.
- **No sound or speech check** here: students speak in a live class (answering, asking), so sound is normal. This is a permission requirement only, and it is separate from whether the host lets students unmute.

## Limits
A level check is crude: it reacts to any loud sound and misses quiet speech. It is advice for the trainer, not proof.
