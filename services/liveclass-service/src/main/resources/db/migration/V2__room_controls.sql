-- =====================================================================
-- Who may switch on a microphone, a camera or a screen share in a class.
--
-- Trainers and staff always may. For students the room has a policy (the
-- three columns on live_sessions) and a host can override it for one person
-- (the nullable columns on live_participants: null means "follow the room").
-- The join token is minted from the effective answer, so it survives a
-- reconnect, and a change made mid-class is pushed to LiveKit at once.
--
-- Screen sharing starts off for students: it is a host-level control that a
-- host grants, not a default.
-- =====================================================================
ALTER TABLE live_sessions
    ADD COLUMN students_can_mic          BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN students_can_camera       BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN students_can_share_screen BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE live_participants
    ADD COLUMN mic_allowed    BOOLEAN,
    ADD COLUMN camera_allowed BOOLEAN,
    ADD COLUMN screen_allowed BOOLEAN;
