-- =====================================================================
-- batch-service :: V1
-- Owns: batches, who is enrolled in them, when they meet, and who
--       attended.
--
-- DESIGN NOTE - a deliberate departure from Doc Section 10.
-- The documentation lists both `batch_students` and `enrollments`. They
-- describe the same fact: this student is in this batch, for this course,
-- with this status. Keeping both means every enrolment is written twice
-- and the two copies eventually disagree - at which point nobody can say
-- which one the attendance register should believe.
-- This schema keeps `enrollments` alone, since it carries everything
-- batch_students did plus the course link and completion date.
-- Recorded in docs/02-documentation-review.md.
-- =====================================================================

CREATE TABLE batches (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_code  VARCHAR(30)  NOT NULL,
    name        VARCHAR(120),
    course_id   BIGINT       NOT NULL,
    course_code VARCHAR(30),
    course_title VARCHAR(160),

    -- Primary trainer. Nullable because a batch is often scheduled before
    -- allocation is settled, and forcing a placeholder trainer produces
    -- timetables attributed to the wrong person.
    trainer_id      BIGINT,
    trainer_user_id BIGINT,
    trainer_name    VARCHAR(160),

    start_date  DATE         NOT NULL,
    end_date    DATE,
    start_time  TIME         NOT NULL,
    end_time    TIME         NOT NULL,
    class_days  VARCHAR(60)  NOT NULL DEFAULT 'MON,TUE,WED,THU,FRI',
    mode        VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE',
    capacity    INTEGER      NOT NULL DEFAULT 30,
    classroom   VARCHAR(60),
    meeting_url VARCHAR(600),
    status      VARCHAR(20)  NOT NULL DEFAULT 'PLANNED',

    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  BIGINT,
    updated_by  BIGINT,

    CONSTRAINT uk_batches_code UNIQUE (batch_code),
    CONSTRAINT ck_batches_mode   CHECK (mode IN ('ONLINE','OFFLINE','HYBRID')),
    CONSTRAINT ck_batches_status CHECK (status IN ('PLANNED','ONGOING','COMPLETED','CANCELLED')),
    CONSTRAINT ck_batches_capacity CHECK (capacity > 0 AND capacity <= 500),
    CONSTRAINT ck_batches_time  CHECK (end_time > start_time),
    CONSTRAINT ck_batches_dates CHECK (end_date IS NULL OR end_date >= start_date),
    -- An online or hybrid batch needs somewhere to meet. liveclass-service
    -- provisions a LiveKit room, so the URL may be filled in later - but a
    -- purely offline batch must not carry a stale meeting link.
    CONSTRAINT ck_batches_classroom CHECK (mode <> 'OFFLINE' OR meeting_url IS NULL)
);
CREATE INDEX ix_batches_course ON batches (course_id);
CREATE INDEX ix_batches_trainer ON batches (trainer_id);
CREATE INDEX ix_batches_status ON batches (status);
CREATE INDEX ix_batches_dates ON batches (start_date, end_date);

-- ---------------------------------------------------------------------
-- Co-trainers. Doc S6.6 says "trainer(s)": a long course is often shared
-- between a primary trainer and specialists for particular modules.
-- ---------------------------------------------------------------------
CREATE TABLE batch_trainers (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        BIGINT      NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    trainer_id      BIGINT      NOT NULL,
    trainer_user_id BIGINT,
    trainer_name    VARCHAR(160),
    role            VARCHAR(20) NOT NULL DEFAULT 'CO_TRAINER',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_batch_trainer UNIQUE (batch_id, trainer_id),
    CONSTRAINT ck_batch_trainer_role CHECK (role IN ('PRIMARY','CO_TRAINER','GUEST'))
);
CREATE INDEX ix_batch_trainers_trainer ON batch_trainers (trainer_id);

-- ---------------------------------------------------------------------
-- Enrolment (Doc S6.6, S14)
-- ---------------------------------------------------------------------
CREATE TABLE enrollments (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    student_code    VARCHAR(30),
    student_name    VARCHAR(160),
    course_id       BIGINT       NOT NULL,
    batch_id        BIGINT       REFERENCES batches (id),
    enrolled_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    completion_date DATE,
    dropped_reason  VARCHAR(255),

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by      BIGINT,
    updated_by      BIGINT,

    CONSTRAINT ck_enrollment_status CHECK (status IN ('ACTIVE','COMPLETED','DROPPED','SUSPENDED','TRANSFERRED'))
);

-- Doc S14: "a student should not be enrolled twice in the same active batch".
-- A partial unique index says exactly that and no more: the same student may
-- be re-enrolled into a batch they previously dropped, which happens when
-- someone repeats a course.
CREATE UNIQUE INDEX uk_enrollment_active_batch
    ON enrollments (student_id, batch_id)
    WHERE status = 'ACTIVE' AND batch_id IS NOT NULL;

CREATE INDEX ix_enrollments_student ON enrollments (student_id);
CREATE INDEX ix_enrollments_batch_status ON enrollments (batch_id, status);
CREATE INDEX ix_enrollments_course ON enrollments (course_id);

-- ---------------------------------------------------------------------
-- Timetable (Doc S6.7)
-- ---------------------------------------------------------------------
CREATE TABLE class_sessions (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id     BIGINT       NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    trainer_id   BIGINT,
    trainer_user_id BIGINT,
    session_date DATE         NOT NULL,
    start_time   TIME         NOT NULL,
    end_time     TIME         NOT NULL,
    topic        VARCHAR(255),
    mode         VARCHAR(20)  NOT NULL DEFAULT 'OFFLINE',
    meeting_url  VARCHAR(600),
    room         VARCHAR(60),
    status       VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',

    -- Set once attendance has been saved, so the trainer's "sessions still
    -- to mark" list is an indexed lookup rather than a NOT EXISTS subquery
    -- against the attendance table.
    attendance_marked BOOLEAN  NOT NULL DEFAULT false,
    -- True when attendance came from liveclass-service rather than from a
    -- trainer ticking boxes. Kept so a correction can be attributed properly.
    attendance_auto   BOOLEAN  NOT NULL DEFAULT false,

    cancelled_reason VARCHAR(255),

    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by   BIGINT,
    updated_by   BIGINT,

    CONSTRAINT ck_session_status CHECK (status IN ('SCHEDULED','COMPLETED','CANCELLED','RESCHEDULED')),
    CONSTRAINT ck_session_mode CHECK (mode IN ('ONLINE','OFFLINE','HYBRID')),
    CONSTRAINT ck_session_time CHECK (end_time > start_time),
    -- One batch cannot have two lectures starting at the same moment.
    CONSTRAINT uk_session_slot UNIQUE (batch_id, session_date, start_time)
);
CREATE INDEX ix_sessions_batch_date ON class_sessions (batch_id, session_date);
CREATE INDEX ix_sessions_trainer_date ON class_sessions (trainer_id, session_date);
CREATE INDEX ix_sessions_date ON class_sessions (session_date);
CREATE INDEX ix_sessions_unmarked ON class_sessions (batch_id, session_date)
    WHERE attendance_marked = false AND status = 'SCHEDULED';

-- ---------------------------------------------------------------------
-- Attendance (Doc S6.9)
-- ---------------------------------------------------------------------
CREATE TABLE attendance (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    session_id BIGINT       NOT NULL REFERENCES class_sessions (id) ON DELETE CASCADE,
    student_id BIGINT       NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    remark     VARCHAR(255),

    -- Where the record came from. An auto-marked row that a trainer later
    -- overrides becomes MANUAL, and the override is audited (Doc S14).
    source     VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    -- Minutes actually present, when known from a live session. Null for
    -- classroom attendance, which is a yes/no judgement.
    attended_minutes INTEGER,

    marked_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    marked_by  BIGINT,
    corrected_at TIMESTAMPTZ,
    corrected_by BIGINT,

    CONSTRAINT uk_attendance_session_student UNIQUE (session_id, student_id),
    CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT','ABSENT','LATE','EXCUSED')),
    CONSTRAINT ck_attendance_source CHECK (source IN ('MANUAL','LIVE_CLASS','IMPORT')),
    CONSTRAINT ck_attendance_minutes CHECK (attended_minutes IS NULL OR attended_minutes >= 0)
);
-- The query behind every student's attendance percentage.
CREATE INDEX ix_attendance_student ON attendance (student_id, status);
CREATE INDEX ix_attendance_session ON attendance (session_id);
