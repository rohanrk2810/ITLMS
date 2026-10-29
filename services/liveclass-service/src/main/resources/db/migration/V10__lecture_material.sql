-- Code and/or a small database a question shows the students, and the trainer's saved lecture material.
ALTER TABLE live_questions
    ADD COLUMN snippet          TEXT,
    ADD COLUMN snippet_language VARCHAR(30),
    ADD COLUMN dataset          TEXT;

-- Material is prepared ahead of a class and sent to the room with one click. The question is kept whole as the same
-- JSON the trainer would have sent live, so sending it is exactly asking it.
CREATE TABLE live_materials (
    id                BIGSERIAL PRIMARY KEY,
    owner_user_id     BIGINT       NOT NULL,
    title             VARCHAR(160) NOT NULL,
    payload           TEXT         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX idx_live_materials_owner ON live_materials (owner_user_id, created_at DESC);
