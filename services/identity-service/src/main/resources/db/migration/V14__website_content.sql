-- The public website's wording (as JSON, edited by the administrator) and the pictures it uses
-- (gallery, hero, faculty, partner logos, course photos). Kept apart from ui_config, which every
-- page load downloads, because this document is larger and only the website reads it.
ALTER TABLE institute_settings ADD COLUMN site_config TEXT NOT NULL DEFAULT '{}';

CREATE TABLE site_images (
    id           BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(40) NOT NULL,
    data         BYTEA       NOT NULL,
    size_bytes   INTEGER     NOT NULL,
    created_by   BIGINT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
