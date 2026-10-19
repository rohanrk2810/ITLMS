-- =====================================================================
-- Recording a class from the host's own browser.
--
-- The host's browser records the class and sends it to the server in pieces while the class runs, so a closed tab
-- loses at most the last few seconds and a finished class is downloadable at once. The file is written to the same
-- recordings volume as a LiveKit Egress capture and served by the same endpoint.
-- =====================================================================
ALTER TABLE live_sessions ADD COLUMN browser_recording_active BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE live_sessions ADD COLUMN recording_chunks         INT     NOT NULL DEFAULT 0;
ALTER TABLE live_sessions ADD COLUMN recording_bytes          BIGINT  NOT NULL DEFAULT 0;
