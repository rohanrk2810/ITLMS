-- =====================================================================
-- Secure test mode.
--
-- A test can be set to "secure": the student's browser watches for the test
-- window being left (another tab or window) and reports it. The first
-- violations warn; when the count reaches max_violations the attempt is
-- terminated - it ends, is scored for the record, and is marked failed.
-- Every reported event is kept with its time so a trainer can review it.
--
-- The browser is the one doing the watching, so a determined student can
-- suppress the reports. The value of the record is in the honest majority,
-- and in the trainer seeing a sitting that reports nothing at all.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN secure_mode     BOOLEAN NOT NULL DEFAULT FALSE,
    -- The violation that ends the attempt: 2 means one warning, then termination.
    ADD COLUMN max_violations  INTEGER NOT NULL DEFAULT 2,
    ADD CONSTRAINT ck_quiz_max_violations CHECK (max_violations BETWEEN 1 AND 10);

ALTER TABLE quiz_attempts DROP CONSTRAINT ck_attempt_status;
ALTER TABLE quiz_attempts ADD CONSTRAINT ck_attempt_status
    CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED', 'TERMINATED'));

ALTER TABLE quiz_attempts
    ADD COLUMN violation_count   INTEGER      NOT NULL DEFAULT 0,
    ADD COLUMN terminated_reason VARCHAR(200);

CREATE TABLE quiz_violations (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id      BIGINT       NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,

    type            VARCHAR(30)  NOT NULL,
    -- Whether this event added to the attempt's violation count. Copying,
    -- right-clicks and leaving fullscreen are recorded but do not end a test;
    -- a second report of the same leaving (tab hidden, then window blurred) is
    -- recorded once as counted and once as not.
    counted         BOOLEAN      NOT NULL,
    detail          VARCHAR(200),

    -- The server's clock decides when it happened; the browser's is kept beside it.
    occurred_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    client_at       TIMESTAMPTZ,

    CONSTRAINT ck_violation_type CHECK (type IN
        ('TAB_SWITCH', 'WINDOW_BLUR', 'FULLSCREEN_EXIT', 'COPY_ATTEMPT', 'PASTE_ATTEMPT',
         'RIGHT_CLICK', 'SHORTCUT_BLOCKED'))
);

CREATE INDEX ix_violation_attempt ON quiz_violations (attempt_id, occurred_at);
