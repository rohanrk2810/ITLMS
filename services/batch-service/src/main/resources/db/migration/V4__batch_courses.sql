-- A batch can run more than one course, each with its own trainer.
--
--   batches.fee          the price of the whole batch; a student placed in it pays this and nothing more for the
--                        courses it holds. Empty means the course fee applies, as before.
--   batch_courses        the courses a batch holds besides the one it was created for (batches.course_id)
--   enrollments.included the student has this course because the batch holds it, not because they bought it: it
--                        carries no fee of its own and does not take a seat
--   enrollments.course_only the student was placed in this one course alone (and pays its own fee), not in the
--                        whole batch; the courses added to the batch later do not reach them
--   class_sessions.course_id  which of the batch's courses a lecture belongs to; empty means batches.course_id

ALTER TABLE batches ADD COLUMN fee NUMERIC(12,2);
ALTER TABLE batches ADD CONSTRAINT ck_batches_fee CHECK (fee IS NULL OR fee >= 0);

CREATE TABLE batch_courses (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    batch_id        BIGINT       NOT NULL REFERENCES batches (id) ON DELETE CASCADE,
    course_id       BIGINT       NOT NULL,
    course_code     VARCHAR(30),
    course_title    VARCHAR(160),
    trainer_id      BIGINT,
    trainer_user_id BIGINT,
    trainer_name    VARCHAR(160),
    added_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uk_batch_course UNIQUE (batch_id, course_id)
);
CREATE INDEX ix_batch_courses_trainer ON batch_courses (trainer_id);

ALTER TABLE enrollments ADD COLUMN included BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE enrollments ADD COLUMN course_only BOOLEAN NOT NULL DEFAULT FALSE;

-- One seat per student per batch: only the placement itself counts, not the courses that come with it.
DROP INDEX uk_enrollment_active_batch;
CREATE UNIQUE INDEX uk_enrollment_active_batch
    ON enrollments (student_id, batch_id)
    WHERE status = 'ACTIVE' AND batch_id IS NOT NULL AND included = FALSE;
CREATE UNIQUE INDEX uk_enrollment_active_batch_course
    ON enrollments (student_id, batch_id, course_id)
    WHERE status = 'ACTIVE' AND batch_id IS NOT NULL AND included = TRUE;

ALTER TABLE class_sessions ADD COLUMN course_id BIGINT;
