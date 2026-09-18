-- =====================================================================
-- finance-service : itilms_finance
--
-- Fee plans, installment schedules and payments (Doc S6.12).
--
-- The rule everything here serves is Doc S14:
--     Fee outstanding = Net Fee - Sum of successful payments
-- It is computed, never stored. A stored balance is a second copy of the
-- truth, and the day a payment is reversed and the balance is not, the
-- institute chases a student for money they already paid.
--
-- Two departures from the document's S10 model, both recorded in
-- docs/02-documentation-review.md:
--   O1  A payment is recorded against the fee plan, not one installment.
--       S10 ties each payment to a single installment, which cannot record
--       a part payment, one payment covering two installments, or paying
--       ahead. Payments are applied to installments oldest-due first.
--   O2  An installment has no stored paid/overdue status. It is derived
--       from the payments by the same oldest-first rule, so it cannot
--       disagree with them.
-- =====================================================================

-- ---------------------------------------------------------------------
-- fee_plans - what one student owes for one course
-- ---------------------------------------------------------------------
CREATE TABLE fee_plans (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    student_id          BIGINT         NOT NULL,
    student_user_id     BIGINT,
    student_code        VARCHAR(40),
    student_name        VARCHAR(160),

    course_id           BIGINT         NOT NULL,
    batch_id            BIGINT,

    total_fee           NUMERIC(12, 2) NOT NULL,
    discount            NUMERIC(12, 2) NOT NULL DEFAULT 0,
    -- Stored for indexing and reports, but pinned by a CHECK to the only
    -- value it can have, so it cannot drift from the two it is made of.
    net_fee             NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',

    status              VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    notes               VARCHAR(500),

    cancelled_at        TIMESTAMPTZ,
    cancelled_reason    VARCHAR(255),

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT ck_plan_total    CHECK (total_fee >= 0),
    CONSTRAINT ck_plan_discount CHECK (discount >= 0 AND discount <= total_fee),
    CONSTRAINT ck_plan_net      CHECK (net_fee = total_fee - discount),
    CONSTRAINT ck_plan_status   CHECK (status IN ('ACTIVE', 'SETTLED', 'CANCELLED')),
    CONSTRAINT ck_plan_cancel   CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL)
);

-- One live plan per student per course. A cancelled plan stays on record
-- and does not block raising a new one.
CREATE UNIQUE INDEX uk_plan_student_course
    ON fee_plans (student_id, course_id)
    WHERE status <> 'CANCELLED';

CREATE INDEX ix_plan_student ON fee_plans (student_id);
CREATE INDEX ix_plan_status  ON fee_plans (status);

-- ---------------------------------------------------------------------
-- fee_installments - when the net fee falls due
-- ---------------------------------------------------------------------
CREATE TABLE fee_installments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_plan_id         BIGINT         NOT NULL REFERENCES fee_plans (id) ON DELETE CASCADE,

    installment_no      INTEGER        NOT NULL,
    due_date            DATE           NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,

    -- Which reminder went out last (7, 3, 1 days before), and when the
    -- student was told it was overdue. Kept so the daily scan sends each
    -- message once rather than every morning.
    last_reminder_days  INTEGER,
    overdue_notified_at TIMESTAMPTZ,

    CONSTRAINT uk_installment_no UNIQUE (fee_plan_id, installment_no),
    CONSTRAINT ck_installment_amount CHECK (amount > 0)
);

CREATE INDEX ix_installment_plan ON fee_installments (fee_plan_id, due_date);
CREATE INDEX ix_installment_due  ON fee_installments (due_date);

-- ---------------------------------------------------------------------
-- payments - money received (Doc S6.12)
-- ---------------------------------------------------------------------
CREATE TABLE payments (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    fee_plan_id         BIGINT         NOT NULL REFERENCES fee_plans (id),
    student_id          BIGINT         NOT NULL,

    amount              NUMERIC(12, 2) NOT NULL,
    payment_date        DATE           NOT NULL,
    method              VARCHAR(20)    NOT NULL,
    -- UPI transaction id, cheque number, bank reference.
    reference_no        VARCHAR(80),
    receipt_no          VARCHAR(40)    NOT NULL,

    -- Only SUCCESS counts toward the outstanding amount (Doc S14 "successful
    -- payments"). A bounced cheque is reversed, never deleted: the receipt
    -- was issued and the record of that has to survive.
    status              VARCHAR(20)    NOT NULL DEFAULT 'SUCCESS',
    reversed_at         TIMESTAMPTZ,
    reversed_by         BIGINT,
    reversal_reason     VARCHAR(255),

    notes               VARCHAR(500),

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_payment_receipt   UNIQUE (receipt_no),
    CONSTRAINT ck_payment_amount    CHECK (amount > 0),
    CONSTRAINT ck_payment_method
        CHECK (method IN ('CASH', 'UPI', 'CARD', 'BANK_TRANSFER', 'CHEQUE', 'ONLINE')),
    CONSTRAINT ck_payment_status    CHECK (status IN ('SUCCESS', 'REVERSED')),
    CONSTRAINT ck_payment_reversal
        CHECK (status <> 'REVERSED' OR (reversed_at IS NOT NULL AND reversal_reason IS NOT NULL))
);

-- The same UPI transaction or cheque entered twice is the most common way a
-- student ends up recorded as having paid double. The bank's reference is
-- unique per method, so the database refuses the second entry.
CREATE UNIQUE INDEX uk_payment_reference
    ON payments (method, reference_no)
    WHERE reference_no IS NOT NULL AND status = 'SUCCESS';

CREATE INDEX ix_payment_plan    ON payments (fee_plan_id, status);
CREATE INDEX ix_payment_student ON payments (student_id);
CREATE INDEX ix_payment_date    ON payments (payment_date);

-- Receipt numbers (RCPT-2026-000123) come from one sequence, so two cashiers
-- recording at the same moment are never handed the same number. The year in
-- the number is the year of issue; the counter itself keeps rising across
-- years rather than restarting, so no number is ever issued twice.
CREATE SEQUENCE receipt_seq START WITH 1 INCREMENT BY 1;
