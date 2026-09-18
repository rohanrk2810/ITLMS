-- =====================================================================
-- notification-service : itilms_notification
--
-- In-app notifications, announcements and email delivery (Doc S16).
--
-- Other services say who should hear about something; this service decides
-- how. To expand "everyone in batch 12" or "every finance user" into people
-- without calling back to other services, it keeps two small read-only
-- copies: recipients (from identity events) and batch_members (from
-- enrolment events). Neither is a source of truth.
--
-- Secrets never land here. A temporary password or a password-reset token
-- arrives in an event, goes straight into an email, and is not written to
-- any table or log.
-- =====================================================================

-- ---------------------------------------------------------------------
-- recipients: who exists, how to email them, and which role they hold.
-- ---------------------------------------------------------------------
CREATE TABLE recipients (
    user_id             BIGINT       PRIMARY KEY,
    email               VARCHAR(160),
    full_name           VARCHAR(160),
    role                VARCHAR(20),
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    -- Status and profile arrive on different topics and can overtake each
    -- other; only a newer status change may overwrite an older one.
    status_changed_at   TIMESTAMPTZ,
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_recipients_role ON recipients (role) WHERE active;

-- ---------------------------------------------------------------------
-- batch_members: which students sit in which batch, for batch and course
-- announcements. Former members are kept (active = false) so a job posted
-- for a course can still reach the people who completed it.
-- ---------------------------------------------------------------------
CREATE TABLE batch_members (
    batch_id    BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    course_id   BIGINT       NOT NULL,
    active      BOOLEAN      NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (batch_id, user_id)
);

CREATE INDEX idx_batch_members_user ON batch_members (user_id);
CREATE INDEX idx_batch_members_course ON batch_members (course_id);

-- ---------------------------------------------------------------------
-- notifications: the bell icon.
-- ---------------------------------------------------------------------
CREATE TABLE notifications (
    id                  BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- The event that caused it. Kafka delivers at least once, so the same
    -- event can arrive twice; the unique key makes the second a no-op
    -- instead of a duplicate in someone's list (and a duplicate email).
    source_event_id     VARCHAR(64)  NOT NULL,
    user_id             BIGINT       NOT NULL,
    type                VARCHAR(40)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    message             TEXT,
    action_url          VARCHAR(500),
    read_at             TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_notification_event_user UNIQUE (source_event_id, user_id)
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_created ON notifications (created_at);

-- ---------------------------------------------------------------------
-- announcements
-- ---------------------------------------------------------------------
CREATE TABLE announcements (
    id              BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    title           VARCHAR(200) NOT NULL,
    message         TEXT         NOT NULL,
    audience        VARCHAR(10)  NOT NULL,
    target_role     VARCHAR(20),
    -- A batch id or a course id, depending on the audience.
    target_id       BIGINT,
    send_email      BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at      TIMESTAMPTZ,
    withdrawn       BOOLEAN      NOT NULL DEFAULT FALSE,
    recipient_count INT          NOT NULL DEFAULT 0,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,

    CONSTRAINT ck_announcement_audience CHECK (audience IN ('ALL', 'ROLE', 'BATCH', 'COURSE')),
    CONSTRAINT ck_announcement_role CHECK ((audience = 'ROLE') = (target_role IS NOT NULL)),
    CONSTRAINT ck_announcement_target CHECK ((audience IN ('BATCH', 'COURSE')) = (target_id IS NOT NULL))
);

CREATE INDEX idx_announcements_created ON announcements (created_at DESC);

-- ---------------------------------------------------------------------
-- email_outbox: emails waiting to go, or already gone.
--
-- Writing the email here in the same transaction as the notification, and
-- sending it afterwards, means a mail server outage delays email without
-- losing it and never loses the in-app notification.
-- ---------------------------------------------------------------------
CREATE TABLE email_outbox (
    id               BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT,
    to_address       VARCHAR(160) NOT NULL,
    subject          VARCHAR(200) NOT NULL,
    text_body        TEXT         NOT NULL,
    html_body        TEXT         NOT NULL,
    status           VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    attempts         INT          NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_error       VARCHAR(500),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),

    CONSTRAINT ck_outbox_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
);

CREATE INDEX idx_outbox_due ON email_outbox (next_attempt_at) WHERE status = 'PENDING';
CREATE INDEX idx_outbox_created ON email_outbox (created_at);
