-- =====================================================================
-- Questions a trainer asks during a live class, and the students' answers.
--
-- offset_seconds is when the question was asked measured from the start of the
-- class (10:35:42 in a class that began at 10:00:00 is 2142 seconds), so that
-- a recording of the class can offer the question at the same moment.
-- Options and answer keys are small JSON arrays kept as text: they are read
-- and written whole and never queried into.
-- =====================================================================
CREATE TABLE live_questions (
    id                BIGSERIAL PRIMARY KEY,
    live_session_id   BIGINT       NOT NULL REFERENCES live_sessions (id) ON DELETE CASCADE,
    class_session_id  BIGINT       NOT NULL,
    batch_id          BIGINT       NOT NULL,
    asked_by_user_id  BIGINT       NOT NULL,
    type              VARCHAR(20)  NOT NULL,
    prompt            TEXT         NOT NULL,
    options           TEXT,
    correct_options   TEXT,
    accepted_answers  TEXT,
    language          VARCHAR(30),
    starter_code      TEXT,
    explanation       TEXT,
    marks             INT          NOT NULL DEFAULT 1,
    status            VARCHAR(10)  NOT NULL DEFAULT 'OPEN',
    asked_at          TIMESTAMPTZ  NOT NULL,
    offset_seconds    INT          NOT NULL DEFAULT 0,
    closed_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_live_questions_session ON live_questions (live_session_id, offset_seconds);
CREATE INDEX idx_live_questions_class_session ON live_questions (class_session_id);

CREATE TABLE live_question_answers (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT       NOT NULL REFERENCES live_questions (id) ON DELETE CASCADE,
    user_id         BIGINT       NOT NULL,
    student_id      BIGINT,
    display_name    VARCHAR(160),
    selected        TEXT,
    answer_text     TEXT,
    code            TEXT,
    language        VARCHAR(30),
    correct         BOOLEAN,
    awarded_marks   INT,
    via_recording   BOOLEAN      NOT NULL DEFAULT FALSE,
    submitted_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_live_answer UNIQUE (question_id, user_id)
);
