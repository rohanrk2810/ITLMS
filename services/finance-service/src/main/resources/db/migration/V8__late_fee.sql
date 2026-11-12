-- A late fee is added once per overdue installment. Remember that it was, so the nightly scan does not charge it again.
ALTER TABLE fee_installments ADD COLUMN late_fee_charged_at TIMESTAMPTZ;
