-- =====================================================================
-- Recording a class with LiveKit Egress.
--
-- egress_id identifies the in-progress capture so it can be stopped and so a
-- late webhook can be matched back to the right class; it is cleared once the
-- capture has finished (successfully or not). recording_file_path is where
-- Egress wrote the file on the shared volume both it and this service mount;
-- recording_url (already on the table since V1) is set only once the file is
-- confirmed complete, which is what the review page checks for.
-- =====================================================================
ALTER TABLE live_sessions
    ADD COLUMN egress_id            VARCHAR(64),
    ADD COLUMN recording_started_at TIMESTAMPTZ,
    ADD COLUMN recording_file_path  VARCHAR(500);
