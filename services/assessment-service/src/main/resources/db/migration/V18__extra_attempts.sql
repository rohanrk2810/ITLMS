-- =====================================================================
-- Extra attempts for one student. A test allows everyone the same number of attempts (quizzes.attempts_allowed) and a
-- published test cannot be edited, so a trainer who wants to give one student another go (a bad connection, a secure test
-- that was ended) records it here. The student's allowance on that test is attempts_allowed plus extra.
-- =====================================================================
CREATE TABLE quiz_extra_attempts (
    quiz_id    BIGINT      NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL,
    extra      INTEGER     NOT NULL CHECK (extra BETWEEN 0 AND 20),
    granted_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (quiz_id, student_id)
);
