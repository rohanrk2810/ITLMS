-- The administrator's wording for the course, copied onto the certificate when it is issued,
-- so a later edit of the course does not change a certificate already given.
ALTER TABLE certificates ADD COLUMN certificate_text VARCHAR(600);
