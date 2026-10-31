-- =====================================================================
-- REMOTE_TOOL: the desktop app found remote-control software (AnyDesk, TeamViewer, ...) running during a secure test.
-- Warned and recorded for the trainer, like the camera events; it does not end the attempt.
-- =====================================================================
ALTER TABLE quiz_violations DROP CONSTRAINT ck_violation_type;
ALTER TABLE quiz_violations ADD CONSTRAINT ck_violation_type CHECK (type IN
    ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
     'RIGHT_CLICK', 'SHORTCUT_BLOCKED',
     'FACE_NOT_DETECTED', 'FACE_OUT_OF_FRAME', 'FACE_MISMATCH', 'DEVICE_DETECTED', 'MULTIPLE_FACES',
     'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED', 'SPEECH_DETECTED',
     'REMOTE_TOOL'));
