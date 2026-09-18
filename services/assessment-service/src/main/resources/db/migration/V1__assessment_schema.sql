-- =====================================================================
-- assessment-service : itilms_assessment
--
-- Assignments and their submissions (Doc S6.10), MCQ tests, attempts and
-- results (Doc S6.11).
--
-- Two rules from Doc S14 shape this schema more than anything else:
--   "Quiz score must be calculated on the server, not trusted from the
--    browser" - so the correct answer lives here and is never part of the
--    payload a student receives while taking a test.
--   "Only the appropriate trainer/staff can see or evaluate submissions
--    belonging to their batches" - so every assignment and quiz names the
--    batch or course it belongs to, and that is what authorization reads.
-- =====================================================================

-- ---------------------------------------------------------------------
-- assignments (Doc S6.10)
-- ---------------------------------------------------------------------
CREATE TABLE assignments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Set for a batch, which is how a trainer normally gives work. Course id
    -- is carried too, so "everything for this course" needs no cross-service
    -- lookup on a listing.
    batch_id            BIGINT       NOT NULL,
    course_id           BIGINT,

    title               VARCHAR(200) NOT NULL,
    instructions        TEXT,
    attachment_ref      VARCHAR(120),

    due_at              TIMESTAMPTZ  NOT NULL,
    max_marks           INTEGER      NOT NULL,

    -- A late submission is accepted and marked LATE rather than refused
    -- (Doc S14). Turning this off refuses it outright, for a timed test-like
    -- task where a deadline has to mean something.
    allow_late          BOOLEAN      NOT NULL DEFAULT TRUE,

    -- Counts toward course completion (Doc S7.3 "required assignments").
    -- The source document has no field for this; see docs/02-documentation-review.md.
    mandatory           BOOLEAN      NOT NULL DEFAULT TRUE,

    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    trainer_id          BIGINT,
    published_at        TIMESTAMPTZ,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT ck_assignment_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_assignment_marks  CHECK (max_marks > 0),
    -- A published assignment must say when it was published; otherwise
    -- "since when could students see this?" has no answer.
    CONSTRAINT ck_assignment_published
        CHECK (status = 'DRAFT' OR published_at IS NOT NULL)
);

CREATE INDEX ix_assignment_batch ON assignments (batch_id, status);
CREATE INDEX ix_assignment_due   ON assignments (due_at);

-- ---------------------------------------------------------------------
-- assignment_submissions
--
-- One row per student per assignment, updated in place when they resubmit
-- before the deadline. The document models a single file_url (S10); this
-- keeps a text answer as well (S6.10 "files/text") and moves attachments to
-- their own table, so a student can hand in a report and its screenshots.
-- ---------------------------------------------------------------------
CREATE TABLE assignment_submissions (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assignment_id       BIGINT       NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,

    student_id          BIGINT       NOT NULL,
    student_name        VARCHAR(160),
    student_user_id     BIGINT,

    text_answer         TEXT,
    submitted_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- How many times this student handed work in. Kept because a trainer
    -- asking "did they change it after I looked?" deserves an answer.
    submission_count    INTEGER      NOT NULL DEFAULT 1,

    -- LATE is decided by the server from due_at at submission time, never
    -- from a timestamp the client sent.
    status              VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',

    marks               INTEGER,
    feedback            TEXT,
    evaluated_at        TIMESTAMPTZ,
    evaluated_by        BIGINT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_submission_student UNIQUE (assignment_id, student_id),
    CONSTRAINT ck_submission_status
        CHECK (status IN ('SUBMITTED', 'LATE', 'EVALUATED', 'RETURNED')),
    CONSTRAINT ck_submission_marks CHECK (marks IS NULL OR marks >= 0),
    -- An evaluated submission carries a mark and a timestamp, or it is not
    -- evaluated. Half-recorded evaluations are how disputes start.
    CONSTRAINT ck_submission_evaluated
        CHECK (status <> 'EVALUATED' OR (marks IS NOT NULL AND evaluated_at IS NOT NULL))
);

CREATE INDEX ix_submission_assignment ON assignment_submissions (assignment_id, status);
CREATE INDEX ix_submission_student    ON assignment_submissions (student_id);

-- File handles only. The bytes live in file-service; this is the reference
-- and enough metadata to render a list without fetching them.
CREATE TABLE submission_files (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    submission_id       BIGINT       NOT NULL
                        REFERENCES assignment_submissions (id) ON DELETE CASCADE,
    file_ref            VARCHAR(120) NOT NULL,
    file_name           VARCHAR(255),
    content_type        VARCHAR(120),
    size_bytes          BIGINT,
    uploaded_at         TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_submission_file UNIQUE (submission_id, file_ref)
);

CREATE INDEX ix_submission_file_submission ON submission_files (submission_id);

-- ---------------------------------------------------------------------
-- quizzes (Doc S6.11)
-- ---------------------------------------------------------------------
CREATE TABLE quizzes (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    course_id               BIGINT       NOT NULL,
    -- Null means the test is set for the course and open to every batch
    -- studying it; a batch id narrows it to one group.
    batch_id                BIGINT,

    title                   VARCHAR(200) NOT NULL,
    instructions            TEXT,

    duration_minutes        INTEGER      NOT NULL,
    pass_percentage         INTEGER      NOT NULL DEFAULT 40,
    attempts_allowed        INTEGER      NOT NULL DEFAULT 1,

    -- Denormalised sum of the questions' marks. Written by the service when
    -- questions change, so scoring and every listing read one number rather
    -- than aggregating on each request.
    total_marks             INTEGER      NOT NULL DEFAULT 0,

    available_from          TIMESTAMPTZ,
    available_until         TIMESTAMPTZ,

    shuffle_questions       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Whether the score is shown the moment the student submits. Off for a
    -- test whose questions are reused with a later batch.
    show_result_immediately BOOLEAN      NOT NULL DEFAULT TRUE,
    mandatory               BOOLEAN      NOT NULL DEFAULT TRUE,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    trainer_id              BIGINT,
    published_at            TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,

    CONSTRAINT ck_quiz_status   CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    CONSTRAINT ck_quiz_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_quiz_pass     CHECK (pass_percentage BETWEEN 0 AND 100),
    CONSTRAINT ck_quiz_attempts CHECK (attempts_allowed > 0),
    CONSTRAINT ck_quiz_window   CHECK (available_until IS NULL OR available_from IS NULL
                                       OR available_until > available_from),
    -- A published test needs questions, so it cannot be published with a
    -- zero total; the service checks the same thing with a clearer message.
    CONSTRAINT ck_quiz_published
        CHECK (status = 'DRAFT' OR (published_at IS NOT NULL AND total_marks > 0))
);

CREATE INDEX ix_quiz_course ON quizzes (course_id, status);
CREATE INDEX ix_quiz_batch  ON quizzes (batch_id, status);

-- ---------------------------------------------------------------------
-- quiz_questions
--
-- The document fixes four columns option_a..option_d and one correct_option
-- (S10), which cannot express true/false, five options, or more than one
-- right answer - and S5 asks for an "MCQ/coding-ready framework". Options
-- move to their own table instead; see docs/02-documentation-review.md.
-- ---------------------------------------------------------------------
CREATE TABLE quiz_questions (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id         BIGINT       NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,

    question_text   TEXT         NOT NULL,
    type            VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_CHOICE',
    marks           INTEGER      NOT NULL DEFAULT 1,
    sequence_no     INTEGER      NOT NULL,

    -- Shown with the result, not while answering.
    explanation     TEXT,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,

    CONSTRAINT uk_question_sequence UNIQUE (quiz_id, sequence_no),
    CONSTRAINT ck_question_type  CHECK (type IN ('SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE')),
    CONSTRAINT ck_question_marks CHECK (marks > 0)
);

CREATE INDEX ix_question_quiz ON quiz_questions (quiz_id, sequence_no);

CREATE TABLE quiz_options (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    question_id     BIGINT       NOT NULL REFERENCES quiz_questions (id) ON DELETE CASCADE,

    option_text     TEXT         NOT NULL,
    -- Never sent to a student before they submit. Scoring happens here.
    is_correct      BOOLEAN      NOT NULL DEFAULT FALSE,
    sequence_no     INTEGER      NOT NULL,

    CONSTRAINT uk_option_sequence UNIQUE (question_id, sequence_no)
);

CREATE INDEX ix_option_question ON quiz_options (question_id, sequence_no);

-- ---------------------------------------------------------------------
-- quiz_attempts
-- ---------------------------------------------------------------------
CREATE TABLE quiz_attempts (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    quiz_id         BIGINT       NOT NULL REFERENCES quizzes (id) ON DELETE CASCADE,

    student_id      BIGINT       NOT NULL,
    student_name    VARCHAR(160),
    student_user_id BIGINT,
    batch_id        BIGINT,

    attempt_no      INTEGER      NOT NULL,

    started_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- started_at + the quiz duration, fixed when the attempt begins. The
    -- deadline is the server's, so closing the laptop and coming back an hour
    -- later does not buy extra time.
    expires_at      TIMESTAMPTZ  NOT NULL,
    submitted_at    TIMESTAMPTZ,

    score           INTEGER,
    percentage      INTEGER,
    passed          BOOLEAN,

    status          VARCHAR(20)  NOT NULL DEFAULT 'IN_PROGRESS',

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,

    CONSTRAINT uk_attempt_number UNIQUE (quiz_id, student_id, attempt_no),
    CONSTRAINT ck_attempt_status CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'EXPIRED')),
    CONSTRAINT ck_attempt_scored
        CHECK (status = 'IN_PROGRESS' OR (score IS NOT NULL AND percentage IS NOT NULL))
);

CREATE INDEX ix_attempt_student ON quiz_attempts (student_id, quiz_id);
CREATE INDEX ix_attempt_quiz    ON quiz_attempts (quiz_id, status);
-- The expiry sweep: attempts still open past their deadline.
CREATE INDEX ix_attempt_open    ON quiz_attempts (expires_at) WHERE status = 'IN_PROGRESS';

-- ---------------------------------------------------------------------
-- quiz_answers - what the student chose, and what it was worth
--
-- Kept per question rather than only as a total, so a student can be shown
-- which ones they got wrong, and a trainer can see which question the whole
-- batch failed.
-- ---------------------------------------------------------------------
CREATE TABLE quiz_answers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    attempt_id      BIGINT       NOT NULL REFERENCES quiz_attempts (id) ON DELETE CASCADE,
    question_id     BIGINT       NOT NULL REFERENCES quiz_questions (id) ON DELETE CASCADE,

    correct         BOOLEAN      NOT NULL DEFAULT FALSE,
    marks_awarded   INTEGER      NOT NULL DEFAULT 0,
    answered_at     TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_answer_question UNIQUE (attempt_id, question_id),
    CONSTRAINT ck_answer_marks CHECK (marks_awarded >= 0)
);

CREATE INDEX ix_answer_attempt ON quiz_answers (attempt_id);

-- One row per option the student ticked. A separate table because a
-- multi-choice answer is several options, and storing them as a delimited
-- string would make "how many people picked option 3" unanswerable in SQL.
CREATE TABLE quiz_answer_options (
    answer_id       BIGINT NOT NULL REFERENCES quiz_answers (id) ON DELETE CASCADE,
    option_id       BIGINT NOT NULL REFERENCES quiz_options (id) ON DELETE CASCADE,

    PRIMARY KEY (answer_id, option_id)
);
