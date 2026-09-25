-- =====================================================================
-- Camera monitoring.
--
-- A test can require the student's camera. Before starting, the student must
-- allow it and show a face; during the test the browser watches the camera
-- and reports when no face (or more than one) is visible, and when the camera
-- is switched off or its permission removed. These are recorded with their
-- times for the trainer to review and each one warns the student, but none
-- of them ends the attempt: a face detector has false alarms, and failing
-- someone on its word alone would be worse than the problem. No images are
-- stored, only the events.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN require_camera BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE quiz_violations DROP CONSTRAINT ck_violation_type;
ALTER TABLE quiz_violations ADD CONSTRAINT ck_violation_type CHECK (type IN
    ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
     'RIGHT_CLICK', 'SHORTCUT_BLOCKED',
     'FACE_NOT_DETECTED', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED'));
