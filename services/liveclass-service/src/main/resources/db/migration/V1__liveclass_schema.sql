-- =====================================================================
-- liveclass-service : itilms_liveclass
--
-- Online classes on a self-hosted LiveKit SFU, plus the join/leave record
-- that makes attendance for an online batch defensible.
--
-- DEVIATION FROM THE SOURCE DOCUMENT: live classes appear in the PDF only
-- as a future enhancement (S22), so it specifies no tables for them. This
-- schema is new work; see docs/02-documentation-review.md.
--
-- Why a separate database: this is the only part of IT-ILMS with a write
-- rate measured in events per second per room. Keeping it apart means a
-- webhook storm during a 100-student class cannot slow the admission desk.
-- =====================================================================

-- ---------------------------------------------------------------------
-- live_sessions - one live room per scheduled class session
-- ---------------------------------------------------------------------
CREATE TABLE live_sessions (
    id                      BIGSERIAL PRIMARY KEY,

    -- The batch-service session this room belongs to. One room per session:
    -- a trainer who reconnects rejoins the same room and their earlier
    -- minutes still count.
    class_session_id        BIGINT       NOT NULL,
    batch_id                BIGINT       NOT NULL,
    batch_code              VARCHAR(40),
    course_title            VARCHAR(200),

    -- Trainer profile id, which is what a trainer's access token carries as
    -- its profile id; the user id is filled in when the room was provisioned
    -- from an event, and may be null when it was provisioned on demand.
    trainer_id              BIGINT,
    trainer_user_id         BIGINT,

    -- The LiveKit room name. Derived from the session id rather than random,
    -- so a room can always be traced back to a class without a lookup.
    room_name               VARCHAR(120) NOT NULL,
    livekit_room_sid        VARCHAR(80),

    topic                   VARCHAR(255),
    session_date            DATE         NOT NULL,
    start_time              TIME         NOT NULL,
    end_time                TIME         NOT NULL,

    -- The same two times as absolute instants, resolved once against the
    -- institute timezone. Every "is the room open yet", "has it finished"
    -- and "which rooms start in the next 10 minutes" query reads these, so
    -- the conversion happens in one place instead of in each query.
    scheduled_start_at      TIMESTAMPTZ  NOT NULL,
    scheduled_end_at        TIMESTAMPTZ  NOT NULL,

    status                  VARCHAR(20)  NOT NULL DEFAULT 'SCHEDULED',
    started_at              TIMESTAMPTZ,
    ended_at                TIMESTAMPTZ,

    max_participants        INT          NOT NULL DEFAULT 100,
    peak_participants       INT          NOT NULL DEFAULT 0,

    recording_enabled       BOOLEAN      NOT NULL DEFAULT FALSE,
    recording_url           VARCHAR(600),

    -- Attendance is computed once, after the room closes. The flag stops a
    -- late webhook or a re-run of the sweep from publishing a second
    -- attendance event and overwriting a trainer manual correction.
    attendance_computed     BOOLEAN      NOT NULL DEFAULT FALSE,
    attendance_computed_at  TIMESTAMPTZ,

    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by              BIGINT,
    updated_by              BIGINT,

    CONSTRAINT uk_live_session_class_session UNIQUE (class_session_id),
    CONSTRAINT uk_live_session_room_name     UNIQUE (room_name),
    CONSTRAINT ck_live_session_status
        CHECK (status IN ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
    CONSTRAINT ck_live_session_times
        CHECK (scheduled_end_at > scheduled_start_at),
    -- A computed session must say when it was computed; otherwise "has this
    -- been published?" has two different answers in the same row.
    CONSTRAINT ck_live_session_computed_consistent
        CHECK (attendance_computed = FALSE OR attendance_computed_at IS NOT NULL)
);

CREATE INDEX idx_live_session_batch   ON live_sessions (batch_id);
CREATE INDEX idx_live_session_trainer ON live_sessions (trainer_id);
CREATE INDEX idx_live_session_date    ON live_sessions (session_date);
CREATE INDEX idx_live_session_status  ON live_sessions (status);

-- The sweep job query: rooms past their end time whose attendance is still
-- outstanding. Partial, because settled sessions are the overwhelming
-- majority and never need to be scanned again.
CREATE INDEX idx_live_session_unsettled
    ON live_sessions (scheduled_end_at)
    WHERE attendance_computed = FALSE AND status <> 'CANCELLED';

-- ---------------------------------------------------------------------
-- live_participants - one row per person per session, totalled
-- ---------------------------------------------------------------------
CREATE TABLE live_participants (
    id                  BIGSERIAL PRIMARY KEY,
    live_session_id     BIGINT       NOT NULL,

    -- The LiveKit identity IT-ILMS mints into the join token: user-<userId>.
    -- Stable per person per room, which is what lets several join/leave
    -- cycles add up to one attendance figure.
    identity            VARCHAR(80)  NOT NULL,
    user_id             BIGINT       NOT NULL,

    -- Student profile id. Null for the trainer and for staff observers, who
    -- are counted in the room but never in the register.
    student_id          BIGINT,
    display_name        VARCHAR(160),
    role                VARCHAR(20)  NOT NULL,

    first_joined_at     TIMESTAMPTZ,
    last_left_at        TIMESTAMPTZ,

    -- Set while the person is in the room, cleared when they leave. Its
    -- presence is how the sweep job finds people whose leave event never
    -- arrived because they closed the laptop lid.
    current_join_at     TIMESTAMPTZ,

    -- Summed across every stint, so a student who dropped twice on bad wifi
    -- is not punished for reconnecting.
    attended_seconds    INT          NOT NULL DEFAULT 0,
    join_count          INT          NOT NULL DEFAULT 0,

    attendance_percent  INT,
    computed_status     VARCHAR(20),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT fk_live_participant_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id) ON DELETE CASCADE,
    CONSTRAINT uk_live_participant_identity UNIQUE (live_session_id, identity),
    CONSTRAINT ck_live_participant_role
        CHECK (role IN ('TRAINER', 'STUDENT', 'STAFF')),
    CONSTRAINT ck_live_participant_seconds CHECK (attended_seconds >= 0)
);

CREATE INDEX idx_live_participant_session ON live_participants (live_session_id);
CREATE INDEX idx_live_participant_student ON live_participants (student_id);
CREATE INDEX idx_live_participant_user    ON live_participants (user_id);

CREATE INDEX idx_live_participant_open
    ON live_participants (live_session_id)
    WHERE current_join_at IS NOT NULL;

-- ---------------------------------------------------------------------
-- live_participant_events - the raw webhook log
--
-- Kept because the totals above are derived. If the threshold rules change,
-- or a trainer disputes a figure, attendance can be recomputed from these
-- rows rather than argued about.
-- ---------------------------------------------------------------------
CREATE TABLE live_participant_events (
    id                  BIGSERIAL PRIMARY KEY,
    live_session_id     BIGINT       NOT NULL,
    identity            VARCHAR(80),
    user_id             BIGINT,
    event_type          VARCHAR(40)  NOT NULL,
    participant_sid     VARCHAR(80),
    occurred_at         TIMESTAMPTZ  NOT NULL,

    -- LiveKit retries a webhook until it gets a 200, so the same delivery can
    -- arrive several times. This id is what makes replay harmless: a repeat
    -- fails the unique constraint and is discarded instead of adding the same
    -- minutes to a student total twice.
    livekit_event_id    VARCHAR(80),

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_live_event_session
        FOREIGN KEY (live_session_id) REFERENCES live_sessions (id) ON DELETE CASCADE,
    CONSTRAINT uk_live_event_livekit_id UNIQUE (livekit_event_id)
);

CREATE INDEX idx_live_event_session ON live_participant_events (live_session_id, occurred_at);
