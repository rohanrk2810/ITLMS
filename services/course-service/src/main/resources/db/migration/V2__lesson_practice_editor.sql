-- =====================================================================
-- Practice editor on a lesson
--
-- Any lesson - a video, a PDF, a note - can carry an embedded code editor
-- for the student to try what they just learned. The lesson stores only WHAT
-- to offer: a language and the code the editor opens with. Running the code is
-- codeexec-service's job, so nothing here ever executes anything.
--
-- Both columns are nullable: most lessons have no editor.
-- =====================================================================
ALTER TABLE lessons
    ADD COLUMN code_language VARCHAR(10),
    ADD COLUMN starter_code  TEXT;

ALTER TABLE lessons
    -- Must match com.itilms.common.code.CodeLanguage.
    ADD CONSTRAINT ck_lesson_code_language CHECK (
        code_language IS NULL
        OR code_language IN ('JAVA','PYTHON','C','CPP','CSHARP','SQL')),
    -- Starter code with no language would open an editor nobody can run.
    ADD CONSTRAINT ck_lesson_starter_needs_language CHECK (
        starter_code IS NULL OR code_language IS NOT NULL);
