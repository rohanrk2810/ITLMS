-- A salary slip with named heads and the employee's details, like the payslips people are used to: several earnings
-- (basic, house rent, allowances, bonus), several deductions (provident fund, professional tax, TDS), and the employee
-- code, PAN, bank, PF and UAN numbers. The old three numbers (basic, allowances, fixed deduction) stay as the totals of
-- the heads, so everything that read them keeps working.

ALTER TABLE staff_pay ADD COLUMN employee_code VARCHAR(40);
ALTER TABLE staff_pay ADD COLUMN pan           VARCHAR(10);
ALTER TABLE staff_pay ADD COLUMN bank_name     VARCHAR(80);
ALTER TABLE staff_pay ADD COLUMN bank_account  VARCHAR(30);
ALTER TABLE staff_pay ADD COLUMN pf_number     VARCHAR(40);
ALTER TABLE staff_pay ADD COLUMN uan           VARCHAR(20);
ALTER TABLE staff_pay ADD COLUMN esi_number    VARCHAR(30);
ALTER TABLE staff_pay ADD COLUMN hired_on      DATE;
ALTER TABLE staff_pay ADD COLUMN department    VARCHAR(80);
ALTER TABLE staff_pay ADD COLUMN location      VARCHAR(120);

-- The heads of a person's pay. A head is a fixed amount, or a percent of the basic (provident fund is usually 12%).
CREATE TABLE staff_pay_heads (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id          BIGINT         NOT NULL,
    kind             VARCHAR(10)    NOT NULL,
    name             VARCHAR(120)   NOT NULL,
    amount           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    percent_of_basic NUMERIC(5, 2),
    is_basic         BOOLEAN        NOT NULL DEFAULT FALSE,
    sort_order       INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT ck_pay_head_kind   CHECK (kind IN ('EARNING', 'DEDUCTION')),
    CONSTRAINT ck_pay_head_amount CHECK (amount >= 0),
    CONSTRAINT ck_pay_head_pct    CHECK (percent_of_basic IS NULL OR (percent_of_basic > 0 AND percent_of_basic <= 100))
);
CREATE INDEX ix_pay_head_user ON staff_pay_heads (user_id, sort_order);

INSERT INTO staff_pay_heads (user_id, kind, name, amount, is_basic, sort_order)
SELECT user_id, 'EARNING', 'Basic', basic, TRUE, 0 FROM staff_pay WHERE basic > 0;
INSERT INTO staff_pay_heads (user_id, kind, name, amount, is_basic, sort_order)
SELECT user_id, 'EARNING', 'Allowances', allowances, FALSE, 1 FROM staff_pay WHERE allowances > 0;
INSERT INTO staff_pay_heads (user_id, kind, name, amount, is_basic, sort_order)
SELECT user_id, 'DEDUCTION', 'Fixed deduction', fixed_deduction, FALSE, 2 FROM staff_pay WHERE fixed_deduction > 0;

-- What a slip was made from: the employee's details as they were that month, and each head with its amount.
ALTER TABLE payslips ADD COLUMN employee_code VARCHAR(40);
ALTER TABLE payslips ADD COLUMN pan           VARCHAR(10);
ALTER TABLE payslips ADD COLUMN bank_name     VARCHAR(80);
ALTER TABLE payslips ADD COLUMN bank_account  VARCHAR(30);
ALTER TABLE payslips ADD COLUMN pf_number     VARCHAR(40);
ALTER TABLE payslips ADD COLUMN uan           VARCHAR(20);
ALTER TABLE payslips ADD COLUMN esi_number    VARCHAR(30);
ALTER TABLE payslips ADD COLUMN hired_on      DATE;
ALTER TABLE payslips ADD COLUMN department    VARCHAR(80);
ALTER TABLE payslips ADD COLUMN location      VARCHAR(120);

CREATE TABLE payslip_items (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slip_id    BIGINT         NOT NULL REFERENCES payslips (id) ON DELETE CASCADE,
    kind       VARCHAR(10)    NOT NULL,
    name       VARCHAR(120)   NOT NULL,
    amount     NUMERIC(12, 2) NOT NULL,
    sort_order INTEGER        NOT NULL DEFAULT 0,
    CONSTRAINT ck_slip_item_kind CHECK (kind IN ('EARNING', 'DEDUCTION'))
);
CREATE INDEX ix_slip_item_slip ON payslip_items (slip_id, sort_order);

INSERT INTO payslip_items (slip_id, kind, name, amount, sort_order)
SELECT id, 'EARNING', 'Basic', basic, 0 FROM payslips WHERE basic > 0;
INSERT INTO payslip_items (slip_id, kind, name, amount, sort_order)
SELECT id, 'EARNING', 'Allowances', allowances, 1 FROM payslips WHERE allowances > 0;
INSERT INTO payslip_items (slip_id, kind, name, amount, sort_order)
SELECT id, 'DEDUCTION', 'Fixed deduction', fixed_deduction, 2 FROM payslips WHERE fixed_deduction > 0;
