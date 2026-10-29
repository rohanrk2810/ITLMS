-- Folders for the question bank. A question is filed by a slash-separated path (Java/OOPs/Coding); the folders table
-- holds the ones a trainer created ahead of filing anything in them, so an empty folder can exist.
ALTER TABLE live_materials ADD COLUMN folder VARCHAR(300);
CREATE INDEX idx_live_materials_folder ON live_materials (owner_user_id, folder);

CREATE TABLE live_material_folders (
    id             BIGSERIAL PRIMARY KEY,
    owner_user_id  BIGINT       NOT NULL,
    path           VARCHAR(300) NOT NULL,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_live_material_folder UNIQUE (owner_user_id, path)
);
