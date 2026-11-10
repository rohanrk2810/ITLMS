-- An online order is either the purchase of a course (COURSE: the fee plan is raised when it is paid, then the student
-- is placed in a batch) or a payment against a fee plan that already exists (FEES: an installment or the balance).
ALTER TABLE online_orders ADD COLUMN kind VARCHAR(8) NOT NULL DEFAULT 'COURSE';
ALTER TABLE online_orders ADD CONSTRAINT ck_online_order_kind CHECK (kind IN ('COURSE', 'FEES'));
