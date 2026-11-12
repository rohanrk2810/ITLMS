-- A student pays the institute's UPI id from their own app, then tells us the transaction reference (UTR). The finance desk
-- checks it against the bank and confirms, which records an ordinary UPI payment and issues the receipt; or rejects it.
CREATE TABLE upi_claims (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_plan_id   BIGINT         NOT NULL REFERENCES fee_plans (id),
    student_id    BIGINT         NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    utr           VARCHAR(40)    NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    decided_by    BIGINT,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(255),
    payment_id    BIGINT REFERENCES payments (id),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT ck_upi_amount CHECK (amount > 0),
    CONSTRAINT ck_upi_status CHECK (status IN ('PENDING', 'CONFIRMED', 'REJECTED'))
);

-- The same transaction reference cannot be claimed twice unless the first claim was rejected.
CREATE UNIQUE INDEX uk_upi_claim_utr ON upi_claims (utr) WHERE status <> 'REJECTED';
CREATE INDEX ix_upi_claim_status ON upi_claims (status);
CREATE INDEX ix_upi_claim_student ON upi_claims (student_id);
