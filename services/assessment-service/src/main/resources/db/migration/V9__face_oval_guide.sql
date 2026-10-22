-- =====================================================================
-- Face oval guide: during a camera test the student keeps their face inside an
-- oval drawn over their own picture. When the face leaves the oval the browser
-- reports FACE_OUT_OF_FRAME at once and the student is warned. Like the other
-- camera events it is recorded for the trainer and never ends the attempt.
-- =====================================================================
ALTER TABLE quiz_violations DROP CONSTRAINT ck_violation_type;
ALTER TABLE quiz_violations ADD CONSTRAINT ck_violation_type CHECK (type IN
    ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
     'RIGHT_CLICK', 'SHORTCUT_BLOCKED',
     'FACE_NOT_DETECTED', 'FACE_OUT_OF_FRAME', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED', 'SPEECH_DETECTED'));
