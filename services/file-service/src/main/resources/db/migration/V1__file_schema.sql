-- =====================================================================
-- file-service : itilms_file
--
-- The bytes live on disk or in MinIO (Doc S17); this table is the only
-- record of what was stored, whose it is, and who may fetch it back
-- (Doc S12: "validate uploads; restrict MIME type and size").
-- =====================================================================

CREATE TABLE files (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- Path inside the storage backend (local directory or MinIO object key).
    -- Never handed to a client; downloads go through /api/files/{id}/download.
    storage_key         VARCHAR(300) NOT NULL,

    original_filename   VARCHAR(255) NOT NULL,
    content_type        VARCHAR(150) NOT NULL,
    size_bytes          BIGINT       NOT NULL,

    category            VARCHAR(30)  NOT NULL,

    -- Whose record this file belongs to (the student a document is about,
    -- the student who submitted an assignment, ...). Not necessarily the
    -- uploader: staff can add a document on a student's behalf.
    owner_user_id       BIGINT       NOT NULL,
    uploaded_by         BIGINT       NOT NULL,

    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_file_storage_key UNIQUE (storage_key),
    CONSTRAINT ck_file_category CHECK (category IN
        ('AVATAR', 'DOCUMENT', 'ASSIGNMENT', 'SUBMISSION', 'LESSON_RESOURCE', 'CERTIFICATE', 'RECEIPT', 'RESUME')),
    CONSTRAINT ck_file_size CHECK (size_bytes > 0)
);

CREATE INDEX ix_file_owner    ON files (owner_user_id);
CREATE INDEX ix_file_category ON files (category);
