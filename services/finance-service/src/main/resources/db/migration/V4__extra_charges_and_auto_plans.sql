-- Two additions to fee plans.
--
-- 1. An installment may carry a name. A charge the finance desk adds on top of the course fee (a development fee, a
--    book, an exam fee) is an installment with a label, so it is paid, reminded about and receipted like any other
--    part of the plan. Course-fee installments have no label.
--
-- 2. A plan can be raised automatically when a student is placed in a batch (before any admission or payment). Such a
--    plan is flagged until someone changes it, so that an admission which arrives later, with an agreed discount, can
--    replace it instead of being ignored.
ALTER TABLE fee_installments ADD COLUMN label VARCHAR(120);
ALTER TABLE fee_plans ADD COLUMN auto_created BOOLEAN NOT NULL DEFAULT FALSE;
