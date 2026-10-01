-- =====================================================================
-- Microphone check, beside the camera check.
--
-- A test can require the student's microphone. Before starting the student
-- allows it; during the test the browser reports when the microphone is turned
-- off or its permission removed, and when sustained sound is picked up (a
-- level check against the room's own background noise, not speech
-- recognition). Like the camera events these warn the student and are kept for
-- the trainer; none of them ends the attempt, because a level meter has false
-- alarms (a cough, a passing lorry). No audio is ever recorded or stored: only
-- the fact that sound was heard, and when.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN require_microphone BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE quiz_violations DROP CONSTRAINT ck_violation_type;
ALTER TABLE quiz_violations ADD CONSTRAINT ck_violation_type CHECK (type IN
    ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
     'RIGHT_CLICK', 'SHORTCUT_BLOCKED',
     'FACE_NOT_DETECTED', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED', 'SPEECH_DETECTED'));
