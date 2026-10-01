-- The picture behind the sign-in card. Optional; without it the page keeps its plain background.
ALTER TABLE institute_settings
    ADD COLUMN login_background      BYTEA,
    ADD COLUMN login_background_type VARCHAR(40);
