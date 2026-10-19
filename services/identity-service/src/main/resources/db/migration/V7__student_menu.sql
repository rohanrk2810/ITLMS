-- Which sidebar items a student does not see. Comma-separated app paths, e.g. "/app/finance,/app/placements".
-- Empty (the default) means students see everything their role allows, as before.
ALTER TABLE institute_settings ADD COLUMN student_hidden_menu TEXT NOT NULL DEFAULT '';
