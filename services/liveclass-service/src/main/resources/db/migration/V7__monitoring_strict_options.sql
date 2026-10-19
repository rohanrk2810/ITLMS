-- =====================================================================
-- Stricter live-class monitoring: each option is its own switch, all off
-- by default so existing settings behave exactly as before.
--
--   tab_switch_check      note when the student leaves the class tab/window
--   attendance_penalty    time with the face missing / tab left does not count as attended
--   strict_severity       more than one face, or a missing face, is CRITICAL at once
--   auto_remove           remove a student from the room after N CRITICAL notes
--   alert_trainer         the trainer's panel raises an alert for each CRITICAL note
-- =====================================================================
ALTER TABLE monitoring_settings ADD COLUMN tab_switch_check          BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_settings ADD COLUMN attendance_penalty        BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_settings ADD COLUMN strict_severity           BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_settings ADD COLUMN auto_remove               BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_settings ADD COLUMN auto_remove_after_critical INT    NOT NULL DEFAULT 3;
ALTER TABLE monitoring_settings ADD COLUMN alert_trainer             BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE monitoring_settings ADD CONSTRAINT ck_monitoring_auto_remove
    CHECK (auto_remove_after_critical BETWEEN 1 AND 20);

ALTER TABLE monitoring_events DROP CONSTRAINT ck_monitoring_event_type;
ALTER TABLE monitoring_events ADD CONSTRAINT ck_monitoring_event_type CHECK (event_type IN
    ('FACE_NOT_DETECTED', 'FACE_RESTORED', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED',
     'MICROPHONE_DISABLED', 'MICROPHONE_PERMISSION_DENIED', 'TAB_AWAY', 'REMOVED_BY_MONITORING'));
