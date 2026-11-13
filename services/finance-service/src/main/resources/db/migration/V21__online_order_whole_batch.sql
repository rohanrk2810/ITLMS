-- An online order can buy a whole batch (every course in it, at the batch fee) instead of one course.
ALTER TABLE online_orders ADD COLUMN whole_batch BOOLEAN NOT NULL DEFAULT FALSE;
