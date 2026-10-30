-- A picture behind every page of the portal (set on the Customize page). Optional.
ALTER TABLE institute_settings
    ADD COLUMN app_background      BYTEA,
    ADD COLUMN app_background_type VARCHAR(40);
