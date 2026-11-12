-- Payroll: what each member of staff is paid, one salary slip per person per month, and the approval and payment of it.
-- Paying a slip records a SALARY expense, so profit and loss already includes the pay.

-- The salary structure of one person: monthly basic, allowances, and a fixed deduction.
CREATE TABLE staff_pay (
    user_id         BIGINT         PRIMARY KEY,
    staff_name      VARCHAR(160)   NOT NULL,
    role_name       VARCHAR(40)    NOT NULL,
    basic           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    allowances      NUMERIC(12, 2) NOT NULL DEFAULT 0,
    fixed_deduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT ck_staff_pay_amounts CHECK (basic >= 0 AND allowances >= 0 AND fixed_deduction >= 0)
);

CREATE SEQUENCE payslip_seq START WITH 1 INCREMENT BY 1;

-- One salary slip for one person for one month. The figures are copied in when it is generated, so changing a
-- structure later does not rewrite slips already made.
CREATE TABLE payslips (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slip_no         VARCHAR(30)    NOT NULL,
    month_key       VARCHAR(7)     NOT NULL,
    user_id         BIGINT         NOT NULL,
    staff_name      VARCHAR(160)   NOT NULL,
    designation     VARCHAR(60)    NOT NULL,
    days_in_month   INTEGER        NOT NULL,
    basic           NUMERIC(12, 2) NOT NULL,
    allowances      NUMERIC(12, 2) NOT NULL,
    fixed_deduction NUMERIC(12, 2) NOT NULL,
    lwp_days        NUMERIC(4, 1)  NOT NULL DEFAULT 0,
    lwp_deduction   NUMERIC(12, 2) NOT NULL DEFAULT 0,
    other_deduction NUMERIC(12, 2) NOT NULL DEFAULT 0,
    bonus           NUMERIC(12, 2) NOT NULL DEFAULT 0,
    gross           NUMERIC(12, 2) NOT NULL,
    net             NUMERIC(12, 2) NOT NULL,
    note            VARCHAR(255),
    status          VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    approved_by     BIGINT,
    approved_at     TIMESTAMPTZ,
    paid_on         DATE,
    pay_method      VARCHAR(20),
    pay_reference   VARCHAR(80),
    expense_id      BIGINT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT,
    CONSTRAINT uk_payslip_no UNIQUE (slip_no),
    CONSTRAINT uk_payslip_month UNIQUE (user_id, month_key),
    CONSTRAINT ck_payslip_net CHECK (net >= 0),
    CONSTRAINT ck_payslip_status CHECK (status IN ('PENDING', 'APPROVED', 'PAID')),
    CONSTRAINT ck_payslip_paid CHECK (status <> 'PAID' OR (paid_on IS NOT NULL AND pay_method IS NOT NULL))
);

CREATE INDEX ix_payslip_month ON payslips (month_key);
