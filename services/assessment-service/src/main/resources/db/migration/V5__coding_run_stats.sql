-- =====================================================================
-- How fast and how light a student's passing solution was, kept per coding
-- question so a student can be shown how it compares with others who solved
-- the same question ("faster than 72%"). One row per student per question, the
-- best figures so far. Only the numbers are kept: no code and no identity is
-- ever shown to anyone else, and the comparison is not shown at all until
-- enough students have solved the question for a percentage to mean something.
-- =====================================================================
CREATE TABLE coding_run_stats (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id BIGINT      NOT NULL,
    student_id  BIGINT      NOT NULL,
    runtime_ms  INT         NOT NULL,
    memory_kb   INT,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_coding_run_stat UNIQUE (question_id, student_id)
);
CREATE INDEX ix_coding_run_stat_question ON coding_run_stats (question_id);
