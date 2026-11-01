-- A person can hold extra duties besides the main role (a trainer who also coordinates). A notification addressed to a
-- role (new student admitted, fee overdue, course request) should reach them too, so the directory keeps the duties.
ALTER TABLE recipients ADD COLUMN extra_roles VARCHAR(100);
