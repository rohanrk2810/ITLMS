-- =====================================================================
-- Video-lesson monitoring. Each lesson opts in; every option is its own switch
-- and all are off by default, so existing lessons behave exactly as before.
--
--   video_monitoring   the student's camera checks that a face is in view while the video plays
--   pause_on_no_face   the video pauses when the face has been missing for face_grace_seconds
--   auto_resume        a paused video carries on by itself when the face is back
--                      (otherwise the student presses Resume)
--
-- lesson_progress also keeps how much was actually watched, which watched_seconds
-- (furthest position) cannot say: skipping to the end sets it to the full length.
-- =====================================================================
ALTER TABLE lessons ADD COLUMN video_monitoring  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lessons ADD COLUMN pause_on_no_face  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lessons ADD COLUMN auto_resume       BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE lessons ADD COLUMN face_grace_seconds INT    NOT NULL DEFAULT 5;
ALTER TABLE lessons ADD CONSTRAINT ck_lessons_face_grace CHECK (face_grace_seconds BETWEEN 2 AND 60);

ALTER TABLE lesson_progress ADD COLUMN played_seconds    INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD COLUMN duration_seconds  INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD COLUMN face_away_seconds INT NOT NULL DEFAULT 0;
ALTER TABLE lesson_progress ADD COLUMN face_pauses       INT NOT NULL DEFAULT 0;
