-- What the institute spends: trainer salaries, rent, bills. Together with the fees collected it gives the profit or loss
-- of a period. An expense entered by mistake is voided with a reason, never deleted.
CREATE TABLE expenses (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    expense_date  DATE           NOT NULL,
    category      VARCHAR(30)    NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    description   VARCHAR(255)   NOT NULL,
    paid_to       VARCHAR(120),
    reference_no  VARCHAR(80),
    voided_at     TIMESTAMPTZ,
    void_reason   VARCHAR(255),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT ck_expense_amount   CHECK (amount > 0),
    CONSTRAINT ck_expense_category
        CHECK (category IN ('SALARY', 'RENT', 'UTILITIES', 'MARKETING', 'SUPPLIES', 'MAINTENANCE', 'OTHER')),
    CONSTRAINT ck_expense_void CHECK (voided_at IS NULL OR void_reason IS NOT NULL)
);

CREATE INDEX ix_expense_date ON expenses (expense_date);
