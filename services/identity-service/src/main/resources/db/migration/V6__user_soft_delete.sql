-- =====================================================================
-- Deleting a user is a soft delete.
--
-- A student's history (fees, attendance, certificates) lives in other services and refers to the user by id, so
-- the row has to stay. "Deleted" means: cannot sign in, name/e-mail/phone wiped (so the phone number and e-mail can
-- be used by a new account), and the status says DELETED. The row keeps its id so old records still point at it.
-- =====================================================================
ALTER TABLE users DROP CONSTRAINT ck_users_status;
ALTER TABLE users ADD CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE','INACTIVE','BLOCKED','DELETED'));
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
