-- A concession (a discount) is given for a reason, and a large one is approved by the administrator. Keep who gave it
-- and why, next to the amount.
ALTER TABLE fee_plans ADD COLUMN discount_reason VARCHAR(255);
ALTER TABLE fee_plans ADD COLUMN discount_set_by BIGINT;
