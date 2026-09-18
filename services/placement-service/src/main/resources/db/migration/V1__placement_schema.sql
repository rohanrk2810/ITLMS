-- =====================================================================
-- placement-service : itilms_placement
--
-- Companies, job openings, applications and the interview pipeline
-- (Doc S6.14, S7.4).
--
-- Doc S14: "Placement application status changes should be auditable." Every
-- stage change is written to application_stage_history in the same
-- transaction as the change itself, so the pipeline's history cannot drift
-- from its current state.
-- =====================================================================

CREATE TABLE companies (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(160) NOT NULL,
    industry        VARCHAR(100),
    website         VARCHAR(255),
    location        VARCHAR(160),
    contact_name    VARCHAR(120),
    contact_email   VARCHAR(160),
    contact_phone   VARCHAR(20),
    notes           TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT
);

-- "Infosys" and "infosys " are the same recruiter; two rows would split
-- their placement history in half.
CREATE UNIQUE INDEX uk_company_name ON companies (LOWER(TRIM(name)));

-- ---------------------------------------------------------------------
-- job_openings
-- ---------------------------------------------------------------------
CREATE TABLE job_openings (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    company_id              BIGINT       NOT NULL REFERENCES companies (id),

    title                   VARCHAR(200) NOT NULL,
    description             TEXT,
    job_type                VARCHAR(20)  NOT NULL DEFAULT 'FULL_TIME',
    location                VARCHAR(160),
    -- As the company states it: "4.5 LPA", "15,000/month stipend".
    package_offered         VARCHAR(100),
    openings                INTEGER,
    application_deadline    DATE,

    -- Eligibility rules (Doc S7.4 "Eligibility Rules Configured"). Enforced
    -- when a student applies, not merely displayed.
    require_certificate     BOOLEAN      NOT NULL DEFAULT FALSE,
    min_attendance_percent  INTEGER,
    min_score_percent       INTEGER,
    eligibility_notes       TEXT,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    published_at            TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,

    CONSTRAINT ck_job_type     CHECK (job_type IN ('FULL_TIME', 'INTERNSHIP', 'CONTRACT', 'PART_TIME')),
    CONSTRAINT ck_job_status   CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CANCELLED')),
    CONSTRAINT ck_job_openings CHECK (openings IS NULL OR openings > 0),
    CONSTRAINT ck_job_attendance CHECK (min_attendance_percent IS NULL OR min_attendance_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_job_score      CHECK (min_score_percent IS NULL OR min_score_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_job_published  CHECK (status = 'DRAFT' OR published_at IS NOT NULL)
);

CREATE INDEX ix_job_company ON job_openings (company_id);
CREATE INDEX ix_job_status  ON job_openings (status, application_deadline);

-- Which courses qualify a student for the job. Empty means any course.
CREATE TABLE job_eligible_courses (
    job_id      BIGINT NOT NULL REFERENCES job_openings (id) ON DELETE CASCADE,
    course_id   BIGINT NOT NULL,
    PRIMARY KEY (job_id, course_id)
);

-- ---------------------------------------------------------------------
-- job_applications
-- ---------------------------------------------------------------------
CREATE TABLE job_applications (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    job_id              BIGINT       NOT NULL REFERENCES job_openings (id),

    student_id          BIGINT       NOT NULL,
    student_user_id     BIGINT,
    student_name        VARCHAR(160),
    -- The course the student qualified through, recorded at application so
    -- the placement summary can say which course got people hired.
    course_id           BIGINT,

    resume_ref          VARCHAR(120),
    cover_note          TEXT,

    applied_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    stage               VARCHAR(20)  NOT NULL DEFAULT 'APPLIED',
    current_round       INTEGER,
    next_interview_at   TIMESTAMPTZ,
    offer_details       VARCHAR(255),
    decided_at          TIMESTAMPTZ,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_application_student UNIQUE (job_id, student_id),
    CONSTRAINT ck_application_stage CHECK (stage IN
        ('APPLIED', 'SHORTLISTED', 'INTERVIEW', 'ON_HOLD', 'SELECTED', 'REJECTED', 'WITHDRAWN'))
);

CREATE INDEX ix_application_job     ON job_applications (job_id, stage);
CREATE INDEX ix_application_student ON job_applications (student_id);

-- ---------------------------------------------------------------------
-- application_stage_history - every move through the pipeline (Doc S14)
-- ---------------------------------------------------------------------
CREATE TABLE application_stage_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id  BIGINT       NOT NULL REFERENCES job_applications (id) ON DELETE CASCADE,
    from_stage      VARCHAR(20),
    to_stage        VARCHAR(20)  NOT NULL,
    round_no        INTEGER,
    note            VARCHAR(500),
    changed_by      BIGINT,
    changed_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX ix_stage_history_application ON application_stage_history (application_id, changed_at);
