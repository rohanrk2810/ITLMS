-- =====================================================================
-- Whether the code editor offers suggestions (autocomplete and snippets) while a student answers a coding question.
-- Off by default: in an exam the author decides, and turns it on only when suggestions are part of what is allowed.
-- =====================================================================
ALTER TABLE quiz_questions ADD COLUMN allow_suggestions BOOLEAN NOT NULL DEFAULT FALSE;
