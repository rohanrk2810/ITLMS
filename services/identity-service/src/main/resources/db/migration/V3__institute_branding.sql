-- One row per deployment: the institute's own name and look. Read anonymously (the login page
-- needs it), written only by an ADMIN. The default name is data, not code.
CREATE TABLE institute_settings (
    id                 SMALLINT PRIMARY KEY CHECK (id = 1),
    name               VARCHAR(120) NOT NULL,
    tagline            VARCHAR(200),
    primary_color      VARCHAR(7),
    contact_email      VARCHAR(160),
    contact_phone      VARCHAR(30),
    website            VARCHAR(200),
    address            VARCHAR(400),
    signatory_name     VARCHAR(120),
    signatory_title    VARCHAR(120),
    logo               BYTEA,
    logo_type          VARCHAR(40),
    favicon            BYTEA,
    favicon_type       VARCHAR(40),
    version            BIGINT       NOT NULL DEFAULT 1,
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by         BIGINT
);
INSERT INTO institute_settings (id, name) VALUES (1, 'IT Institute LMS');
