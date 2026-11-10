-- How long a student may use a course, separately from the batch dates.
--
-- access_until is optional. Empty means no limit, which is how every enrolment behaved until now, so nobody loses
-- access when this migration runs. When a date is set, a nightly job closes the enrolment (EXPIRED) after it, and an
-- administrator can extend it, which opens the enrolment again.
ALTER TABLE enrollments ADD COLUMN access_until DATE;

ALTER TABLE enrollments DROP CONSTRAINT ck_enrollment_status;
ALTER TABLE enrollments ADD CONSTRAINT ck_enrollment_status
    CHECK (status IN ('ACTIVE', 'COMPLETED', 'DROPPED', 'SUSPENDED', 'TRANSFERRED', 'EXPIRED'));

CREATE INDEX ix_enrollments_access_until ON enrollments (access_until)
    WHERE status = 'ACTIVE' AND access_until IS NOT NULL;
