# 23 - Live class: subtitles, transcript, End class, and who can see whom

liveclass-service (V9 migration) and the live room page.

## Subtitles and a written record of what the trainer said
While the class runs, the **trainer's browser** turns speech into text, shows it to everyone in the room as subtitles, and sends each finished phrase to the server. When the class is over the phrases are one text file.

- **Recognition** is the browser's own speech recogniser (Web Speech API: Chrome and Edge). It is free and needs nothing on our servers, but the browser vendor's service does the recognising, so the trainer's audio goes to that vendor and not to this institute. This app only ever receives words.
- **The trainer** presses **Start subtitles** in the room's top bar and picks the language they teach in (Marathi `mr-IN`, Hindi `hi-IN`, English `en-IN`). Only the class's trainer sees this control; staff observers do not.
- **Everyone else** sees the subtitles across the bottom of the class, and a **Subtitles on/off** button (on by default; the choice is kept in the browser).
- **Live delivery:** the phrase being spoken (not final) and each finished phrase travel over the LiveKit data channel on the topic `captions`. The overlay accepts a message only from a participant whose token metadata says `TRAINER`: every participant may publish data, so without this check a student could put words on the class's screen.
- **Saving:** finished phrases are queued in the trainer's browser and sent every 4 seconds to `POST /api/liveclass/sessions/{id}/transcript` (at most 40 per request). Each carries a `clientId`, so a retry is stored once. Pressing **End class** flushes what is still waiting before the room closes.
- **Rules on the server (`TranscriptService`):** only the class's host (trainer of that batch, or staff) may write; the class must be `LIVE` (a transcript is what was said during the class, nothing can be added afterwards); blank phrases are dropped; at most 20,000 phrases per class; the time of each phrase is held between the class start and now, so a wrong browser clock cannot put it before the class or in the future.
- **Where it is stored:** table `live_transcript_lines` (class, running number, seconds from the start, language, text, client id) in the liveclass database, plus a counter `live_sessions.transcript_lines`. No audio is stored anywhere.
- **Getting the file:** `GET /api/liveclass/sessions/{id}/transcript` returns a UTF-8 text file (class title, course and batch, then one `[hh:mm:ss] text` line per phrase). It is built when asked for, not kept as a file. Allowed: the class's trainer, staff, and a student enrolled in the batch. 404 when nothing was captured. The **Transcript** button appears on a past class (Live classes page) and on the class review page when `transcriptLines > 0`.

### Limits
- Chrome or Edge only for the trainer; the tab must stay open and online.
- Marathi mixed with English (the way most classes are taught) produces mistakes, and technical words are often written wrongly. The file says so in its header. Treat it as notes, not a verbatim record.
- If the trainer's connection to the speech service drops, subtitles stop and restart by themselves; words spoken meanwhile are lost.

## End class
A red **End class** button in the room's top bar, for the class's trainer and for staff (`POST /api/liveclass/sessions/{id}/end`, which already existed). It asks first, because it cannot be undone. Ending the class settles attendance at once (instead of waiting for the room to empty), closes the LiveKit room (everyone is disconnected and sent back to the Live classes page) and writes the audit entry. A finished class cannot be joined again.

## Who can see whom in the room
Who has joined is for the people running the class. A **student's** view is built from the trainer's and staff's camera and screen share and the student's own picture only: no tile, placeholder, name or head-count for other students (`StudentConference`; the top-bar count is shown only to trainers and staff). Trainers and staff see the normal room and the host side panel.

This is done in the browser. A student with developer tools could still read LiveKit's participant list; hiding it at the server would need a separate approach.
