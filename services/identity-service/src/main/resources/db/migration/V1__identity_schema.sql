-- =====================================================================
-- identity-service :: V1
-- Owns: accounts, credentials, refresh tokens, password resets.
-- Does NOT own: student/trainer profiles (admission-service).
-- =====================================================================

CREATE TABLE users (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name           VARCHAR(80)  NOT NULL,
    last_name            VARCHAR(80)  NOT NULL,
    email                VARCHAR(160) NOT NULL,
    phone                VARCHAR(20),
    password_hash        VARCHAR(100) NOT NULL,
    role                 VARCHAR(30)  NOT NULL,
    status               VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    -- Replicated from admission-service via ProfileLinkedEvent so the access
    -- token can carry the student/trainer id without a cross-service call on
    -- the login path. Read-only here: this service never writes it from a
    -- request, only from the event stream.
    profile_id           BIGINT,
    profile_code         VARCHAR(30),

    -- Brute-force protection. Cleared on every successful sign-in.
    failed_attempts      INTEGER      NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,

    must_change_password BOOLEAN      NOT NULL DEFAULT false,
    last_login_at        TIMESTAMPTZ,

    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           BIGINT,
    updated_by           BIGINT,

    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT ck_users_role   CHECK (role IN ('ADMIN','COORDINATOR','TRAINER','STUDENT','PLACEMENT','FINANCE')),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED')),
    CONSTRAINT ck_users_failed_attempts CHECK (failed_attempts >= 0)
);

-- Login accepts email or phone, so both need to be fast and case-insensitive
-- on email. A functional index keeps lower(email) lookups off a sequential scan.
CREATE UNIQUE INDEX uk_users_email_lower ON users (lower(email));
CREATE INDEX ix_users_role_status ON users (role, status);
CREATE INDEX ix_users_profile ON users (profile_id) WHERE profile_id IS NOT NULL;
CREATE INDEX ix_users_name_search ON users (lower(first_name), lower(last_name));

-- ---------------------------------------------------------------------
-- Refresh tokens.
-- Only the SHA-256 hash is stored. A database dump therefore does not hand
-- an attacker a set of working sessions, which is the same reasoning that
-- applies to passwords (Doc S12).
-- ---------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by VARCHAR(100),
    user_agent  VARCHAR(255),
    ip_address  VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_expiry ON refresh_tokens (expires_at);

-- ---------------------------------------------------------------------
-- Password reset tokens. Single use, short lived, hashed like the above.
-- ---------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  VARCHAR(100) NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    used_at     TIMESTAMPTZ,
    requested_ip VARCHAR(45),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_reset_token_hash UNIQUE (token_hash)
);
CREATE INDEX ix_reset_tokens_user ON password_reset_tokens (user_id);
