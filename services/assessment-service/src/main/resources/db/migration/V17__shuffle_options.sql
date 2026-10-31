-- =====================================================================
-- Shuffle the answer options as well as the questions: each student sees the options of a multiple-choice question in
-- their own order (fixed for the attempt, so a reload shows the same order). On by default.
-- =====================================================================
ALTER TABLE quizzes
    ADD COLUMN shuffle_options BOOLEAN NOT NULL DEFAULT TRUE;
