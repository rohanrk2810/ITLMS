-- =====================================================================
-- A SQL question can come with its own database: the CREATE TABLE and INSERT statements the author writes. They run
-- first, on a fresh database, and the student's query runs after them, so the student only writes the query.
-- =====================================================================
ALTER TABLE quiz_questions ADD COLUMN setup_sql TEXT;
