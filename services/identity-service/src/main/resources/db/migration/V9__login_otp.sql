-- One-time codes for sign-in by WhatsApp. Only a hash of the code is kept; a request is closed after too many wrong
-- guesses (attempts) or when it is used or expires.
CREATE TABLE login_otps (
    id            BIGSERIAL PRIMARY KEY,
    phone_digits  VARCHAR(20)  NOT NULL,
    code_hash     VARCHAR(100) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    used_at       TIMESTAMPTZ,
    attempts      INT          NOT NULL DEFAULT 0,
    requested_ip  VARCHAR(45),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_otps_phone ON login_otps (phone_digits, created_at DESC);
