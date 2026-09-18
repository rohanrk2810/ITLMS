-- =====================================================================
-- course-service :: V1
-- Owns: the course catalog, its curriculum, and each student's progress
--       through it.
--
-- Progress lives here rather than in batch-service because it is measured
-- against lessons, and lessons are this service's data. Splitting them
-- would mean a join across a service boundary on every dashboard load.
-- =====================================================================

CREATE TABLE courses (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title             VARCHAR(160)  NOT NULL,
    code              VARCHAR(30)   NOT NULL,
    summary           VARCHAR(500),
    description       TEXT,
    learning_outcomes TEXT,
    prerequisites     TEXT,
    technology_stack  VARCHAR(400),
    duration_hours    INTEGER       NOT NULL DEFAULT 0,
    level             VARCHAR(20)   NOT NULL DEFAULT 'BEGINNER',
    fee               NUMERIC(12,2) NOT NULL DEFAULT 0,
    thumbnail_ref     VARCHAR(64),
    status            VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    published_at      TIMESTAMPTZ,

    created_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,

    CONSTRAINT uk_courses_code UNIQUE (code),
    CONSTRAINT ck_courses_level  CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    CONSTRAINT ck_courses_status CHECK (status IN ('DRAFT','PUBLISHED','ARCHIVED')),
    CONSTRAINT ck_courses_fee    CHECK (fee >= 0),
    CONSTRAINT ck_courses_duration CHECK (duration_hours >= 0),
    -- Doc S6.5: a published course is visible to students, so it must not be
    -- publishable without the metadata the catalog page needs. The service
    -- checks this too, with a message that says which field is missing; the
    -- constraint is the backstop for anything that bypasses the service.
    CONSTRAINT ck_courses_published_metadata CHECK (
        status <> 'PUBLISHED'
        OR (description IS NOT NULL AND length(trim(description)) > 0
            AND summary IS NOT NULL AND length(trim(summary)) > 0
            AND published_at IS NOT NULL))
);
CREATE INDEX ix_courses_status ON courses (status);
CREATE INDEX ix_courses_level ON courses (level);
CREATE INDEX ix_courses_title ON courses (lower(title));

-- ---------------------------------------------------------------------
-- Curriculum
-- ---------------------------------------------------------------------
CREATE TABLE course_modules (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id   BIGINT       NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
    title       VARCHAR(160) NOT NULL,
    description TEXT,
    sequence_no INTEGER      NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,
    CONSTRAINT uk_module_sequence UNIQUE (course_id, sequence_no),
    CONSTRAINT ck_module_sequence CHECK (sequence_no > 0)
);
CREATE INDEX ix_modules_course ON course_modules (course_id, sequence_no);

CREATE TABLE lessons (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    module_id        BIGINT       NOT NULL REFERENCES course_modules (id) ON DELETE CASCADE,
    title            VARCHAR(160) NOT NULL,
    type             VARCHAR(20)  NOT NULL,
    content_url      VARCHAR(600),
    content_file_ref VARCHAR(64),
    text_content     TEXT,
    duration_minutes INTEGER      NOT NULL DEFAULT 0,
    sequence_no      INTEGER      NOT NULL,

    -- Doc S6.8 / S8.1: a preview lesson is readable without enrolment, so a
    -- prospective student can sample the course from the public catalog.
    is_preview       BOOLEAN      NOT NULL DEFAULT false,
    -- Only mandatory lessons count toward completion (Doc S7.3). Optional
    -- extras must not hold a certificate hostage.
    is_mandatory     BOOLEAN      NOT NULL DEFAULT true,

    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_by       BIGINT,

    CONSTRAINT uk_lesson_sequence UNIQUE (module_id, sequence_no),
    CONSTRAINT ck_lesson_type CHECK (type IN ('VIDEO','PDF','NOTE','LINK','TEXT')),
    CONSTRAINT ck_lesson_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_lesson_duration CHECK (duration_minutes >= 0),
    -- A lesson with no body of any kind is a heading, not material. Catching
    -- it here stops students opening an empty player and reporting a bug.
    CONSTRAINT ck_lesson_has_content CHECK (
        content_url IS NOT NULL
        OR content_file_ref IS NOT NULL
        OR (text_content IS NOT NULL AND length(trim(text_content)) > 0))
);
CREATE INDEX ix_lessons_module ON lessons (module_id, sequence_no);

-- ---------------------------------------------------------------------
-- Enrolment mirror.
--
-- batch-service owns enrolment. This table is a local copy, populated from
-- EnrollmentCreatedEvent, that exists so progress can be recorded and
-- queried without calling another service on every lesson a student opens.
-- It holds only what progress needs.
-- ---------------------------------------------------------------------
CREATE TABLE course_enrollments (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id     BIGINT        NOT NULL,
    student_id        BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    course_id         BIGINT        NOT NULL REFERENCES courses (id),
    batch_id          BIGINT,
    status            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    progress_percent  NUMERIC(5,2)  NOT NULL DEFAULT 0,
    completed_lessons INTEGER       NOT NULL DEFAULT 0,
    total_lessons     INTEGER       NOT NULL DEFAULT 0,
    completed_at      TIMESTAMPTZ,
    enrolled_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),

    CONSTRAINT uk_course_enrollment UNIQUE (enrollment_id),
    CONSTRAINT ck_course_enrollment_status CHECK (status IN ('ACTIVE','COMPLETED','DROPPED','SUSPENDED')),
    CONSTRAINT ck_course_enrollment_progress CHECK (progress_percent BETWEEN 0 AND 100)
);
CREATE INDEX ix_course_enrollments_student ON course_enrollments (student_id);
CREATE INDEX ix_course_enrollments_course ON course_enrollments (course_id);
CREATE INDEX ix_course_enrollments_batch ON course_enrollments (batch_id);

-- ---------------------------------------------------------------------
-- Per-lesson progress (Doc S7.2)
-- ---------------------------------------------------------------------
CREATE TABLE lesson_progress (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id   BIGINT      NOT NULL REFERENCES course_enrollments (id) ON DELETE CASCADE,
    lesson_id       BIGINT      NOT NULL REFERENCES lessons (id) ON DELETE CASCADE,
    completed       BOOLEAN     NOT NULL DEFAULT false,
    watched_seconds INTEGER     NOT NULL DEFAULT 0,
    completed_at    TIMESTAMPTZ,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uk_progress_enrollment_lesson UNIQUE (enrollment_id, lesson_id),
    CONSTRAINT ck_progress_seconds CHECK (watched_seconds >= 0)
);
CREATE INDEX ix_progress_enrollment ON lesson_progress (enrollment_id);
-- Answers "how many lessons has this enrolment finished" from the index
-- alone, which is the query behind every student dashboard.
CREATE INDEX ix_progress_completed ON lesson_progress (enrollment_id, completed)
    WHERE completed = true;
