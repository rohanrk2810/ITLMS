-- =====================================================================
-- Certificate requests: a student asks, an ADMIN approves or rejects, and only
-- then is a certificate issued. Before this a student who met the criteria
-- issued their own certificate on the spot.
--
-- Isolation between institutes is by deployment (one database per institute),
-- so there is no tenant_id column; see docs/13-branding.md.
-- =====================================================================

CREATE TABLE certificate_requests (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    student_id          BIGINT       NOT NULL,
    student_user_id     BIGINT,
    student_code        VARCHAR(40),
    student_name        VARCHAR(160) NOT NULL,

    course_id           BIGINT       NOT NULL,
    course_title        VARCHAR(200) NOT NULL,
    -- From the student's own enrolment, never from the request body.
    batch_id            BIGINT,
    batch_name          VARCHAR(160),

    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    requested_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    -- The criteria as they stood when the student asked (JSON). The reviewer
    -- also sees them re-checked live, since a payment can be reversed after.
    eligibility_snapshot TEXT,

    reviewed_by         BIGINT,
    reviewed_at         TIMESTAMPTZ,
    rejection_reason    VARCHAR(500),
    certificate_id      BIGINT REFERENCES certificates (id),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT ck_certificate_request_status
        CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'ISSUED')),
    CONSTRAINT ck_certificate_request_rejection
        CHECK (status <> 'REJECTED' OR rejection_reason IS NOT NULL)
);

-- One open request per student per course. A rejected one does not block
-- asking again; an issued one is covered by the valid-certificate index.
CREATE UNIQUE INDEX uk_certificate_request_open
    ON certificate_requests (student_id, course_id)
    WHERE status IN ('PENDING', 'APPROVED');

CREATE INDEX ix_certificate_request_student ON certificate_requests (student_id);
CREATE INDEX ix_certificate_request_status  ON certificate_requests (status, requested_at DESC);

ALTER TABLE certificates
    ADD COLUMN batch_name VARCHAR(160),
    ADD COLUMN request_id BIGINT REFERENCES certificate_requests (id);
