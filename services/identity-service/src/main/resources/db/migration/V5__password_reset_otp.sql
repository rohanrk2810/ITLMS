-- Password reset now uses a 6-digit emailed code (OTP) instead of a link token.
-- A 6-digit code is not unique across users, so the old uniqueness rule goes;
-- the code is verified against the account's own latest request, and the
-- attempts counter locks a request after too many wrong guesses.
ALTER TABLE password_reset_tokens DROP CONSTRAINT uk_reset_token_hash;
CREATE INDEX ix_reset_tokens_hash ON password_reset_tokens (token_hash);
ALTER TABLE password_reset_tokens ADD COLUMN attempts INT NOT NULL DEFAULT 0;
