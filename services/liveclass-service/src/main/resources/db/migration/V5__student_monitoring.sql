-- =====================================================================
-- Live-class student monitoring: a camera-based check that the student's face
-- stays visible. Off unless an ADMIN switches it on.
--
-- A setting can be put at four levels. The most specific one that exists wins:
-- the class itself, then its batch, then its course, then the institute.
-- No row at all means monitoring is OFF, so nothing is monitored by default.
--
-- The check itself runs in the student's browser; the server stores what it
-- reports (never any picture) and decides who may report and who may read it.
-- =====================================================================
CREATE TABLE monitoring_settings (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    scope_type            VARCHAR(12)  NOT NULL,
    -- 0 for the institute, otherwise the course, batch or class-session id.
    scope_id              BIGINT       NOT NULL DEFAULT 0,

    enabled               BOOLEAN      NOT NULL,
    face_visibility       BOOLEAN      NOT NULL DEFAULT TRUE,
    -- true: the student cannot join without allowing the camera.
    camera_required       BOOLEAN      NOT NULL DEFAULT FALSE,
    -- How long the face must be missing before the student is warned.
    warning_after_seconds INT          NOT NULL DEFAULT 10,
    show_warning          BOOLEAN      NOT NULL DEFAULT TRUE,
    warning_message       VARCHAR(200),
    log_events            BOOLEAN      NOT NULL DEFAULT TRUE,

    updated_by            BIGINT,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by            BIGINT,

    CONSTRAINT uk_monitoring_scope UNIQUE (scope_type, scope_id),
    CONSTRAINT ck_monitoring_scope_type CHECK (scope_type IN ('INSTITUTE', 'COURSE', 'BATCH', 'SESSION')),
    CONSTRAINT ck_monitoring_warning_after CHECK (warning_after_seconds BETWEEN 3 AND 300)
);

CREATE TABLE monitoring_events (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    live_session_id   BIGINT       NOT NULL REFERENCES live_sessions (id),
    student_id        BIGINT       NOT NULL,
    student_user_id   BIGINT       NOT NULL,
    student_name      VARCHAR(160),

    event_type        VARCHAR(30)  NOT NULL,
    severity          VARCHAR(10)  NOT NULL,
    occurred_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Seconds into the class, so a recording can be opened at the same moment.
    offset_seconds    INT          NOT NULL,
    duration_seconds  INT,
    detail            VARCHAR(255),

    CONSTRAINT ck_monitoring_event_type CHECK (event_type IN
        ('FACE_NOT_DETECTED', 'FACE_RESTORED', 'MULTIPLE_FACES', 'CAMERA_DISABLED', 'CAMERA_PERMISSION_DENIED')),
    CONSTRAINT ck_monitoring_severity CHECK (severity IN ('INFO', 'WARNING', 'CRITICAL'))
);

CREATE INDEX ix_monitoring_event_session ON monitoring_events (live_session_id, occurred_at);
CREATE INDEX ix_monitoring_event_student ON monitoring_events (live_session_id, student_id);
