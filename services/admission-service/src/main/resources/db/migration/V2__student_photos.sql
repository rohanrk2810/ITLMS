-- =====================================================================
-- admission-service :: V2
-- The student's own photo, printed on the ID card.
--
-- Kept in its own table so a list of students never loads image bytes.
-- students.has_photo tells the screens whether to offer the upload: a student
-- may add the photo once, after that only staff can replace it.
-- =====================================================================
ALTER TABLE students ADD COLUMN has_photo BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE student_photos (
    student_id   BIGINT      PRIMARY KEY REFERENCES students (id) ON DELETE CASCADE,
    photo        BYTEA       NOT NULL,
    content_type VARCHAR(40) NOT NULL,
    updated_by   BIGINT,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
