-- =====================================================================
-- Question pool: a test may hold more questions than one student gets. Each attempt draws its own set at random when it
-- starts and keeps it (question_ids), so a reload shows the same paper and the marks are counted against that set
-- (max_marks). questions_per_attempt is the size of the draw (null = every question); attempt_marks is what an attempt is
-- worth, worked out when the test is published, for the page to show before a student starts.
--
-- Typing stats: how much of each coding answer was typed and how much pasted, kept as the edits arrive so the trainer's
-- results sheet can flag a pasted answer without reading every replay.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN questions_per_attempt INTEGER,
    ADD COLUMN attempt_marks         INTEGER,
    ADD CONSTRAINT ck_quiz_pool CHECK (questions_per_attempt IS NULL OR questions_per_attempt >= 1);

ALTER TABLE quiz_attempts
    ADD COLUMN question_ids TEXT,
    ADD COLUMN max_marks    INTEGER;

CREATE TABLE quiz_typing_stats (
    attempt_id    BIGINT  NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    question_id   BIGINT  NOT NULL,
    typed_chars   INTEGER NOT NULL DEFAULT 0,
    pasted_chars  INTEGER NOT NULL DEFAULT 0,
    largest_paste INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (attempt_id, question_id)
);
