-- =====================================================================
-- Subtitles and a written record of what the trainer said.
--
-- The trainer's browser turns speech into text while the class runs, shows it to the room as subtitles, and sends
-- each finished phrase here. When the class is over the phrases are one text file, for the trainer, staff and the
-- students of the batch. No audio is sent or stored: only the words.
-- =====================================================================
CREATE TABLE live_transcript_lines (
    id              BIGSERIAL    PRIMARY KEY,
    live_session_id BIGINT       NOT NULL REFERENCES live_sessions (id) ON DELETE CASCADE,
    seq             INT          NOT NULL,
    at_seconds      INT          NOT NULL,
    language        VARCHAR(10),
    text            VARCHAR(1000) NOT NULL,
    client_id       VARCHAR(40),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_transcript_seq UNIQUE (live_session_id, seq),
    CONSTRAINT uq_transcript_client UNIQUE (live_session_id, client_id)
);

ALTER TABLE live_sessions ADD COLUMN transcript_lines INT NOT NULL DEFAULT 0;
