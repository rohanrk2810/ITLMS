-- =====================================================================
-- Two more things the camera can notice during a test, both only warned about and recorded for the trainer:
--   FACE_MISMATCH     the face in front of the camera is not the one recorded at the start of the test
--   DEVICE_DETECTED   a mobile phone is in view
-- Like the other camera events neither ends the attempt: a detector in a browser has false alarms.
-- =====================================================================
ALTER TABLE quiz_violations DROP CONSTRAINT ck_violation_type;
ALTER TABLE quiz_violations ADD CONSTRAINT ck_violation_type CHECK (type IN
    ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
     'RIGHT_CLICK', 'SHORTCUT_BLOCKED',
     'FACE_NOT_DETECTED', 'FACE_OUT_OF_FRAME', 'FACE_MISMATCH', 'DEVICE_DETECTED', 'MULTIPLE_FACES',
     'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED', 'SPEECH_DETECTED'));
