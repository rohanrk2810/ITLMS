-- A student's own sidebar choice, set by an administrator. NULL: follow the institute's student menu
-- (institute_settings.student_hidden_menu). Otherwise a comma-separated list of hidden app paths, possibly empty
-- (this student sees everything), and it replaces the institute's list for them.
ALTER TABLE users ADD COLUMN hidden_menu TEXT;
