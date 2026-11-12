-- A refund is not finished when the administrator approves it: the money still has to be handed back. Record when, how
-- and by whom, so the books show the refund as paid out, and a cash refund reduces that day's cash.
ALTER TABLE refunds ADD COLUMN paid_on DATE;
ALTER TABLE refunds ADD COLUMN pay_method VARCHAR(20);
ALTER TABLE refunds ADD COLUMN pay_reference VARCHAR(80);
ALTER TABLE refunds ADD COLUMN paid_by BIGINT;
ALTER TABLE refunds DROP CONSTRAINT ck_refund_status;
ALTER TABLE refunds ADD CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED', 'PAID'));
ALTER TABLE refunds ADD CONSTRAINT ck_refund_paid CHECK (status <> 'PAID' OR (paid_on IS NOT NULL AND pay_method IS NOT NULL));
