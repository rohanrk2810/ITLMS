-- =====================================================================
-- Identity cards for students, trainers and other staff.
--
-- An admin or coordinator issues a card; the printed PDF is drawn from this row, so a revoked card cannot be
-- printed again from here. The person's name, code and batch are copied onto the card at issue (a card shows what
-- was true when it was printed). Issuing a new card for the same person revokes the old one.
--
-- The photo (a small JPEG or PNG) is kept in its own table so listing cards never reads image bytes, and is stored
-- here so drawing the PDF never depends on another service.
-- Isolation between institutes is by deployment, so there is no tenant_id column.
-- =====================================================================

CREATE SEQUENCE id_card_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE id_cards (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    card_no           VARCHAR(40)  NOT NULL,
    verification_code VARCHAR(16)  NOT NULL,

    -- STUDENT, TRAINER or STAFF. person_id is the admission-service student or trainer id; staff have none.
    person_type       VARCHAR(10)  NOT NULL,
    person_id         BIGINT,
    user_id           BIGINT,
    person_code       VARCHAR(40),
    full_name         VARCHAR(160) NOT NULL,
    designation       VARCHAR(120),

    batch_id          BIGINT,
    batch_name        VARCHAR(160),

    has_photo         BOOLEAN      NOT NULL DEFAULT FALSE,

    issued_on         DATE         NOT NULL,
    valid_until       DATE         NOT NULL,

    status            VARCHAR(10)  NOT NULL DEFAULT 'ISSUED',
    revoked_at        TIMESTAMPTZ,
    revoked_by        BIGINT,
    revoked_reason    VARCHAR(255),
    issued_by         BIGINT,

    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by        BIGINT,
    updated_by        BIGINT,

    CONSTRAINT uq_id_cards_no UNIQUE (card_no),
    CONSTRAINT ck_id_cards_type CHECK (person_type IN ('STUDENT', 'TRAINER', 'STAFF')),
    CONSTRAINT ck_id_cards_status CHECK (status IN ('ISSUED', 'REVOKED'))
);

CREATE INDEX ix_id_cards_person ON id_cards (person_type, person_id);
CREATE INDEX ix_id_cards_user ON id_cards (user_id);

CREATE TABLE id_card_photos (
    card_id      BIGINT      NOT NULL PRIMARY KEY REFERENCES id_cards (id) ON DELETE CASCADE,
    photo        BYTEA       NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
