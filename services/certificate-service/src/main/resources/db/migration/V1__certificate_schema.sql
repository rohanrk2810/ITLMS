-- =====================================================================
-- certificate-service : itilms_certificate
--
-- Course certificates and their public verification (Doc S6.13, S7.3).
--
-- Doc S14: "Certificate issue must validate all configured completion
-- criteria on the server." The criteria are checked against the services
-- that own each fact - lesson progress, test and assignment results,
-- attendance, fees - at the moment of issue, and what was found is kept
-- with the certificate as evidence.
-- =====================================================================

CREATE TABLE certificates (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Printed on the certificate, sequential: ITILMS-2026-0000123.
    certificate_no      VARCHAR(40)  NOT NULL,

    -- Printed beside the number, and required to verify it publicly.
    -- Certificate numbers are sequential, so on their own anyone could walk
    -- the verification page from 1 upwards and collect every graduate's
    -- name. The code is random and unguessable; knowing the number without
    -- it reveals nothing. See docs/02-documentation-review.md, O5.
    verification_code   VARCHAR(16)  NOT NULL,

    student_id          BIGINT       NOT NULL,
    student_user_id     BIGINT,
    student_code        VARCHAR(40),
    student_name        VARCHAR(160) NOT NULL,

    course_id           BIGINT       NOT NULL,
    course_title        VARCHAR(200) NOT NULL,
    batch_id            BIGINT,

    issue_date          DATE         NOT NULL,
    completion_date     DATE,

    status              VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    revoked_at          TIMESTAMPTZ,
    revoked_by          BIGINT,
    revoked_reason      VARCHAR(255),

    -- The figures the criteria were checked against at issue - attendance
    -- percentage, average score, and so on - as JSON. When a certificate is
    -- questioned a year later, this is the answer to "on what basis?".
    evidence            TEXT,

    issued_by           BIGINT,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_certificate_no     UNIQUE (certificate_no),
    CONSTRAINT ck_certificate_status CHECK (status IN ('ISSUED', 'REVOKED')),
    CONSTRAINT ck_certificate_revoked
        CHECK (status <> 'REVOKED' OR (revoked_at IS NOT NULL AND revoked_reason IS NOT NULL))
);

-- One valid certificate per student per course. A revoked one stays on
-- record, and does not stop a corrected certificate being issued.
CREATE UNIQUE INDEX uk_certificate_student_course
    ON certificates (student_id, course_id)
    WHERE status = 'ISSUED';

CREATE INDEX ix_certificate_student ON certificates (student_id);
CREATE INDEX ix_certificate_course  ON certificates (course_id);

CREATE SEQUENCE certificate_seq START WITH 1 INCREMENT BY 1;
