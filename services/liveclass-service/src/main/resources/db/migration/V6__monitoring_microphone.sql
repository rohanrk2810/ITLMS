-- =====================================================================
-- Microphone requirement for live-class monitoring.
--
-- "Microphone required" asks the student to allow the microphone before joining,
-- and notes it if the permission is later removed or the microphone stops. It
-- does NOT listen for speech: students talk in a live class (answering, asking),
-- so sound is normal there. Nothing is recorded by this check.
-- =====================================================================
ALTER TABLE monitoring_settings ADD COLUMN microphone_required BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE monitoring_events DROP CONSTRAINT ck_monitoring_event_type;
ALTER TABLE monitoring_events ADD CONSTRAINT ck_monitoring_event_type CHECK (event_type IN
    ('FACE_NOT_DETECTED', 'FACE_RESTORED', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED'));
