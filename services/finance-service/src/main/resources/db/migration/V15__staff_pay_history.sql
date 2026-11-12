-- Every time someone's salary structure is set, keep what it was set to, and who set it and when. The audit log has it
-- too, but this is what the payroll screen shows beside the person.
CREATE TABLE staff_pay_history (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT         NOT NULL,
    basic           NUMERIC(12, 2) NOT NULL,
    allowances      NUMERIC(12, 2) NOT NULL,
    fixed_deduction NUMERIC(12, 2) NOT NULL,
    active          BOOLEAN        NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by      BIGINT,
    updated_by      BIGINT
);

CREATE INDEX ix_staff_pay_history_user ON staff_pay_history (user_id, id DESC);
