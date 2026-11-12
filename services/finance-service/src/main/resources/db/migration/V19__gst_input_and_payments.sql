-- GST paid on what the institute buys (rent, supplies) is a credit against the GST it owes, and what it pays the
-- government is a payment against that liability. Both are recorded so the GST return shows what is really payable.
ALTER TABLE expenses ADD COLUMN gst_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE expenses ADD CONSTRAINT ck_expense_gst CHECK (gst_amount >= 0 AND gst_amount <= amount);

CREATE TABLE gst_payments (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paid_on    DATE           NOT NULL,
    for_month  VARCHAR(7),
    amount     NUMERIC(12, 2) NOT NULL,
    method     VARCHAR(20)    NOT NULL,
    reference  VARCHAR(80),
    note       VARCHAR(255),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by BIGINT,
    updated_by BIGINT,
    CONSTRAINT ck_gst_payment_amount CHECK (amount > 0)
);

CREATE INDEX ix_gst_payment_date ON gst_payments (paid_on);
