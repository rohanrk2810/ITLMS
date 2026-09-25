-- =====================================================================
-- Two more kinds of question: a typed short answer, and a coding question
-- graded by running the student's program against test cases.
--
-- Short answer: the trainer lists the accepted answers; a typed answer is
-- marked right if it equals one of them after trimming, collapsing spaces and
-- ignoring case. No accepted answer, no way to mark it, so one is required.
--
-- Coding: a question has a language, optional starter code and test cases
-- (standard input, expected output, a weight, and whether the student may see
-- it). A student's marks are the share of test-case weight their program
-- passed. The verdict is stored on the answer beside the exact code it was
-- computed from, so a time-out or a trainer closing the test can be scored
-- without running anything.
-- =====================================================================
ALTER TABLE quiz_questions DROP CONSTRAINT ck_question_type;
ALTER TABLE quiz_questions ADD CONSTRAINT ck_question_type CHECK (type IN
    ('SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE', 'SHORT_ANSWER', 'CODING'));

ALTER TABLE quiz_questions
    ADD COLUMN code_language VARCHAR(10),
    ADD COLUMN starter_code  TEXT;

CREATE TABLE quiz_accepted_answers (
    question_id     BIGINT       NOT NULL REFERENCES quiz_questions (id) ON DELETE CASCADE,
    sequence_no     INTEGER      NOT NULL,
    answer_text     VARCHAR(500) NOT NULL,
    PRIMARY KEY (question_id, sequence_no)
);

CREATE TABLE quiz_test_cases (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id     BIGINT       NOT NULL REFERENCES quiz_questions (id) ON DELETE CASCADE,
    sequence_no     INTEGER      NOT NULL,

    -- Fed to the program's standard input; empty for a program that reads nothing.
    input           TEXT         NOT NULL DEFAULT '',
    expected_output TEXT         NOT NULL,
    -- A hidden case shows the student only pass or fail, never its input or expected output.
    hidden          BOOLEAN      NOT NULL DEFAULT FALSE,
    weight          INTEGER      NOT NULL DEFAULT 1,

    CONSTRAINT uk_test_case_sequence UNIQUE (question_id, sequence_no),
    CONSTRAINT ck_test_case_weight CHECK (weight > 0)
);

CREATE INDEX ix_test_case_question ON quiz_test_cases (question_id, sequence_no);

ALTER TABLE quiz_answers
    ADD COLUMN answer_text        TEXT,
    ADD COLUMN tests_passed       INTEGER,
    ADD COLUMN tests_total        INTEGER,
    -- SHA-256 of the code the verdict below was computed from.
    ADD COLUMN tested_source_hash VARCHAR(64),
    ADD COLUMN tested_marks       INTEGER;
