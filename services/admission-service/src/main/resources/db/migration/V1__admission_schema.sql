-- =====================================================================
-- admission-service :: V1
-- Owns: leads and their follow-ups, student profiles, trainer profiles,
--       and uploaded identity/education documents.
--
-- user_id columns reference identity-service and carry no foreign key:
-- the two services own separate databases, so referential integrity across
-- that boundary is maintained by events, not by the engine. The column is
-- still indexed and unique, because it is looked up constantly.
-- =====================================================================

-- ---------------------------------------------------------------------
-- Leads (Doc S6.4)
-- ---------------------------------------------------------------------
CREATE TABLE leads (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name                 VARCHAR(120) NOT NULL,
    phone                VARCHAR(20)  NOT NULL,
    email                VARCHAR(160),
    source               VARCHAR(40)  NOT NULL,
    interested_course_id BIGINT,
    counselor_user_id    BIGINT,
    status               VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    next_follow_up_at    TIMESTAMPTZ,
    notes                TEXT,
    lost_reason          VARCHAR(255),
    converted_student_id BIGINT,
    converted_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,

    CONSTRAINT ck_leads_source CHECK (source IN
        ('WALK_IN','WEBSITE','REFERRAL','PHONE','SOCIAL_MEDIA','CAMPAIGN','SELF_REGISTRATION','OTHER')),
    CONSTRAINT ck_leads_status CHECK (status IN
        ('NEW','CONTACTED','FOLLOW_UP','INTERESTED','NOT_INTERESTED','CONVERTED','LOST')),
    -- A converted lead must say what it converted into, and an unconverted
    -- one must not claim a student. Enforced here because the pair is the
    -- single most important fact on the row.
    CONSTRAINT ck_leads_conversion CHECK (
        (status = 'CONVERTED' AND converted_student_id IS NOT NULL AND converted_at IS NOT NULL)
        OR (status <> 'CONVERTED' AND converted_student_id IS NULL))
);

-- The counselor's working view is "my open leads, soonest follow-up first".
-- This index is what makes that list instant rather than a full scan.
CREATE INDEX ix_leads_counselor_followup ON leads (counselor_user_id, next_follow_up_at)
    WHERE status NOT IN ('CONVERTED','LOST','NOT_INTERESTED');
CREATE INDEX ix_leads_status ON leads (status);
CREATE INDEX ix_leads_phone ON leads (phone);
CREATE INDEX ix_leads_created ON leads (created_at DESC);

-- ---------------------------------------------------------------------
-- Follow-up history.
-- A separate table rather than overwriting a "last contacted" column: the
-- sequence of attempts is what tells a manager whether a lead was worked
-- properly or quietly dropped.
-- ---------------------------------------------------------------------
CREATE TABLE lead_followups (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lead_id        BIGINT      NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    contacted_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    outcome        VARCHAR(30) NOT NULL,
    remark         TEXT,
    next_action_at TIMESTAMPTZ,
    created_by     BIGINT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_followup_outcome CHECK (outcome IN
        ('CALLED','NO_ANSWER','VISITED','EMAILED','MESSAGED','MEETING','OTHER'))
);
CREATE INDEX ix_lead_followups_lead ON lead_followups (lead_id, contacted_at DESC);

-- ---------------------------------------------------------------------
-- Students (Doc S6.2)
-- ---------------------------------------------------------------------
CREATE TABLE students (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT       NOT NULL,
    student_code      VARCHAR(30)  NOT NULL,

    -- Denormalised from identity-service so a roster can be rendered without
    -- resolving hundreds of names over HTTP. Kept current by consuming
    -- UserUpdatedEvent; identity-service remains the source of truth.
    full_name         VARCHAR(160) NOT NULL,
    email             VARCHAR(160),
    phone             VARCHAR(20),

    date_of_birth     DATE,
    gender            VARCHAR(20),
    highest_education VARCHAR(160),
    college           VARCHAR(160),
    graduation_year   INTEGER,
    address_line      VARCHAR(255),
    city              VARCHAR(80),
    state             VARCHAR(80),
    pincode           VARCHAR(12),
    guardian_name     VARCHAR(120),
    guardian_phone    VARCHAR(20),
    emergency_contact VARCHAR(20),
    admission_date    DATE         NOT NULL DEFAULT CURRENT_DATE,
    admission_source  VARCHAR(40),
    status            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    remarks           TEXT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by        BIGINT,
    updated_by        BIGINT,

    CONSTRAINT uk_students_user UNIQUE (user_id),
    CONSTRAINT uk_students_code UNIQUE (student_code),
    CONSTRAINT ck_students_gender CHECK (gender IS NULL OR gender IN ('MALE','FEMALE','OTHER')),
    CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE','ALUMNI','DROPPED','SUSPENDED')),
    CONSTRAINT ck_students_grad_year CHECK (graduation_year IS NULL OR graduation_year BETWEEN 1950 AND 2100)
);
CREATE INDEX ix_students_status ON students (status);
CREATE INDEX ix_students_name ON students (lower(full_name));
CREATE INDEX ix_students_admission_date ON students (admission_date DESC);

ALTER TABLE leads ADD CONSTRAINT fk_leads_student
    FOREIGN KEY (converted_student_id) REFERENCES students (id);

-- ---------------------------------------------------------------------
-- Trainers (Doc S6.3)
-- ---------------------------------------------------------------------
CREATE TABLE trainers (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT       NOT NULL,
    employee_code    VARCHAR(30)  NOT NULL,
    full_name        VARCHAR(160) NOT NULL,
    email            VARCHAR(160),
    phone            VARCHAR(20),
    specialization   VARCHAR(200),
    qualification    VARCHAR(200),
    experience_years NUMERIC(4,1) NOT NULL DEFAULT 0,
    bio              TEXT,
    joined_on        DATE,
    status           VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by       BIGINT,
    updated_by       BIGINT,

    CONSTRAINT uk_trainers_user UNIQUE (user_id),
    CONSTRAINT uk_trainers_code UNIQUE (employee_code),
    CONSTRAINT ck_trainers_status CHECK (status IN ('ACTIVE','INACTIVE','ON_LEAVE')),
    CONSTRAINT ck_trainers_experience CHECK (experience_years >= 0 AND experience_years <= 70)
);
CREATE INDEX ix_trainers_status ON trainers (status);
CREATE INDEX ix_trainers_name ON trainers (lower(full_name));

-- ---------------------------------------------------------------------
-- Which courses a trainer is qualified to teach. Coordinators use this
-- when assigning a batch; without it, trainer allocation is guesswork.
-- course_id points at course-service.
-- ---------------------------------------------------------------------
CREATE TABLE trainer_courses (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trainer_id BIGINT      NOT NULL REFERENCES trainers (id) ON DELETE CASCADE,
    course_id  BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uk_trainer_course UNIQUE (trainer_id, course_id)
);
CREATE INDEX ix_trainer_courses_course ON trainer_courses (course_id);

-- ---------------------------------------------------------------------
-- Document references. The bytes live in file-service; this table records
-- what each file is and who it belongs to.
-- ---------------------------------------------------------------------
CREATE TABLE student_documents (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id   BIGINT       NOT NULL REFERENCES students (id) ON DELETE CASCADE,
    document_type VARCHAR(40) NOT NULL,
    file_ref     VARCHAR(64)  NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    verified     BOOLEAN      NOT NULL DEFAULT false,
    verified_by  BIGINT,
    verified_at  TIMESTAMPTZ,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_type CHECK (document_type IN
        ('ID_PROOF','ADDRESS_PROOF','PHOTO','MARKSHEET','DEGREE','RESUME','OTHER'))
);
CREATE INDEX ix_student_documents_student ON student_documents (student_id);
