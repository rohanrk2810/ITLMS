-- =====================================================================
-- A coding question can let the student pick the language they answer in.
-- Off by default: the author fixes the language, and only turns this on when
-- the question is about the logic (the test cases are plain input and output,
-- so any language can be checked against them).
-- The language a student actually used is kept beside their code, so marking
-- the answer runs the same language they wrote it in.
-- =====================================================================
ALTER TABLE quiz_questions ADD COLUMN allow_language_choice BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE quiz_answers   ADD COLUMN code_language VARCHAR(10);
