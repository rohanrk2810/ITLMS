-- Lecture material becomes a question bank: tagged, searchable, and shareable between trainers.
ALTER TABLE live_materials
    ADD COLUMN course_id   BIGINT,
    ADD COLUMN topic       VARCHAR(120),
    ADD COLUMN tags        VARCHAR(400),
    ADD COLUMN difficulty  VARCHAR(10),
    ADD COLUMN shared      BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN owner_name  VARCHAR(160);

CREATE INDEX idx_live_materials_shared ON live_materials (shared) WHERE shared;
