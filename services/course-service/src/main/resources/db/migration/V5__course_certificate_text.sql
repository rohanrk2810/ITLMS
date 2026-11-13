-- What a certificate for this course says under the course title, written by the administrator.
-- Empty means the certificate carries only the standard wording.
ALTER TABLE courses ADD COLUMN certificate_text VARCHAR(600);
