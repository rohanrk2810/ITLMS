-- =====================================================================
-- A student asks to join a course; an administrator or coordinator decides.
--
-- Lives in batch-service because approving a request IS an enrolment, and
-- enrolments, batches and seat counts are already owned here. Student and
-- course details are copied onto the row when the request is made, so the
-- reviewer sees what the student saw even if either is edited later.
-- =====================================================================
CREATE TABLE course_requests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    student_id          BIGINT       NOT NULL,
    user_id             BIGINT       NOT NULL,
    student_code        VARCHAR(30),
    student_name        VARCHAR(160),
    student_email       VARCHAR(160),
    student_phone       VARCHAR(30),

    course_id           BIGINT       NOT NULL,
    course_code         VARCHAR(40),
    course_title        VARCHAR(200),

    -- The batch the student would like, if they chose one. The reviewer may
    -- approve into a different batch of the same course.
    preferred_batch_id  BIGINT       REFERENCES batches (id),
    message             VARCHAR(1000),

    status              VARCHAR(12)  NOT NULL DEFAULT 'PENDING',
    decided_by          BIGINT,
    decided_by_name     VARCHAR(160),
    decided_at          TIMESTAMPTZ,
    decision_note       VARCHAR(500),
    approved_batch_id   BIGINT       REFERENCES batches (id),
    enrollment_id       BIGINT       REFERENCES enrollments (id),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT ck_course_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'))
);

-- One open request per student per course. A rejected or cancelled one does
-- not block asking again.
CREATE UNIQUE INDEX uk_course_request_pending
    ON course_requests (student_id, course_id)
    WHERE status = 'PENDING';

CREATE INDEX ix_course_requests_student ON course_requests (student_id, id DESC);
CREATE INDEX ix_course_requests_status ON course_requests (status, id DESC);
