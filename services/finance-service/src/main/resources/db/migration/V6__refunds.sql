-- Refunds. A payment is never edited or deleted; a refund is a request that an administrator approves, and approving it
-- records how much of the payment has been given back. What the student has paid is the payment minus what was refunded
-- on it, so the outstanding amount, the dashboard and the receipts' balance all follow without a second copy.
ALTER TABLE payments ADD COLUMN refunded_amount NUMERIC(12, 2) NOT NULL DEFAULT 0;
ALTER TABLE payments ADD CONSTRAINT ck_payment_refunded CHECK (refunded_amount >= 0 AND refunded_amount <= amount);

CREATE TABLE refunds (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id    BIGINT         NOT NULL REFERENCES payments (id),
    fee_plan_id   BIGINT         NOT NULL,
    student_id    BIGINT         NOT NULL,
    amount        NUMERIC(12, 2) NOT NULL,
    reason        VARCHAR(255)   NOT NULL,
    status        VARCHAR(20)    NOT NULL DEFAULT 'REQUESTED',
    decided_by    BIGINT,
    decided_at    TIMESTAMPTZ,
    decision_note VARCHAR(255),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT ck_refund_amount CHECK (amount > 0),
    CONSTRAINT ck_refund_status CHECK (status IN ('REQUESTED', 'APPROVED', 'REJECTED'))
);

CREATE INDEX ix_refund_payment ON refunds (payment_id);
CREATE INDEX ix_refund_status  ON refunds (status);
