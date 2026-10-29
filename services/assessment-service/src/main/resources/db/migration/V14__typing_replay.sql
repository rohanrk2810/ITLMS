-- =====================================================================
-- Typing replay.
--
-- A test can ask the student's browser to record how each coding answer was typed: every edit with its time. The
-- trainer replays it afterwards, and a solution that appears all at once (pasted) stands out from one that was
-- written. The edits are kept in chunks, as the browser sends them, and only for tests that switch it on.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN record_typing BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE quiz_typing_chunks (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id  BIGINT      NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    question_id BIGINT      NOT NULL,
    -- The browser numbers its chunks from 0 per question; a chunk that arrives twice is kept once.
    seq         INTEGER     NOT NULL CHECK (seq >= 0),
    -- A JSON array of edits: [time_ms, offset, removed_length, inserted_text, pasted(0|1)]
    events      TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_typing_chunk UNIQUE (attempt_id, question_id, seq)
);
