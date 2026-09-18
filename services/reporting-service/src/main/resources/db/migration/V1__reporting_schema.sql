-- =====================================================================
-- reporting-service : itilms_reporting
--
-- Three read models, each fed by events from the services that own the
-- facts (Doc S15). Nothing here is written by a person; every row traces
-- back to something another service published.
-- =====================================================================

-- The institute-wide audit trail (Doc S5, S12, S14). Every service
-- publishes AuditRecordedEvent; this is the only place it is read back
-- from. event_id de-duplicates a redelivered Kafka message.
CREATE TABLE audit_logs (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id       VARCHAR(64)  NOT NULL,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    service_name   VARCHAR(60)  NOT NULL,
    actor_user_id  BIGINT,
    actor_email    VARCHAR(160),
    actor_role     VARCHAR(30),
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(60),
    entity_id      VARCHAR(60),
    old_value      TEXT,
    new_value      TEXT,
    ip_address     VARCHAR(45),
    user_agent     VARCHAR(255),
    recorded_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_audit_event_id UNIQUE (event_id)
);

CREATE INDEX ix_audit_service_action ON audit_logs (service_name, action);
CREATE INDEX ix_audit_entity         ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_occurred_at    ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_actor          ON audit_logs (actor_user_id);

-- Dashboard facts (Doc S15): one row per event that a counter is built
-- from - admissions, enrolments, payments, results, certificates,
-- placements. Grouped by metric_key at read time rather than kept as a
-- running total, so a redelivered event cannot inflate a count twice.
CREATE TABLE activity_events (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id     VARCHAR(64)    NOT NULL,
    metric_key   VARCHAR(60)    NOT NULL,
    occurred_at  TIMESTAMPTZ    NOT NULL,
    event_date   DATE           NOT NULL,
    amount       NUMERIC(14,2),
    ref_id       BIGINT,
    dimension    VARCHAR(60),

    CONSTRAINT uk_activity_event UNIQUE (event_id, metric_key)
);

CREATE INDEX ix_activity_metric_date ON activity_events (metric_key, event_date);

-- The current status of one student in one session, mirrored from
-- AttendanceMarkedEvent. Keyed by (student, session) - not incremented
-- counts - so a correction (Doc S6.9, S14) is applied by upserting the
-- same row rather than double-counting the old and new status.
CREATE TABLE student_session_attendance (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    student_id    BIGINT      NOT NULL,
    session_id    BIGINT      NOT NULL,
    batch_id      BIGINT      NOT NULL,
    session_date  DATE        NOT NULL,
    status        VARCHAR(20) NOT NULL,
    marked_at     TIMESTAMPTZ NOT NULL,

    CONSTRAINT uk_student_session UNIQUE (student_id, session_id),
    CONSTRAINT ck_attendance_status CHECK (status IN ('PRESENT', 'ABSENT', 'LATE', 'EXCUSED'))
);

CREATE INDEX ix_attendance_student ON student_session_attendance (student_id);
CREATE INDEX ix_attendance_batch   ON student_session_attendance (batch_id);
