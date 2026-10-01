-- A practice lesson can let the student pick the language to practise in. Off unless the author turns it on.
ALTER TABLE lessons ADD COLUMN allow_language_choice BOOLEAN NOT NULL DEFAULT FALSE;
