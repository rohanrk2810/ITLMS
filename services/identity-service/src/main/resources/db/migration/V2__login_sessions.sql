-- A refresh-token row doubles as the login session record. session_id stays the same
-- through every rotation, so "one device signed in once" is one session no matter how
-- many times its token has been swapped.
ALTER TABLE refresh_tokens
    ADD COLUMN session_id       VARCHAR(36),
    ADD COLUMN device_label     VARCHAR(120),
    ADD COLUMN last_activity_at TIMESTAMPTZ,
    ADD COLUMN session_started_at TIMESTAMPTZ,
    ADD COLUMN revoke_reason    VARCHAR(30);

UPDATE refresh_tokens
   SET session_id = gen_random_uuid()::text,
       last_activity_at = created_at,
       session_started_at = created_at;

ALTER TABLE refresh_tokens
    ALTER COLUMN session_id SET NOT NULL,
    ALTER COLUMN last_activity_at SET NOT NULL,
    ALTER COLUMN session_started_at SET NOT NULL;

CREATE INDEX ix_refresh_tokens_session ON refresh_tokens (session_id);
