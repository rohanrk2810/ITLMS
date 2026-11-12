-- The usual fee structure of a course: how many installments, how soon the first falls due, and the extra heads
-- (a development fee, a book) that every student of the course is charged. When a student is placed in a batch the
-- plan raised for them follows this, instead of one lump sum due in a week.
CREATE TABLE course_fee_templates (
    course_id         BIGINT      PRIMARY KEY,
    installment_count INTEGER     NOT NULL DEFAULT 1,
    first_due_days    INTEGER     NOT NULL DEFAULT 7,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_by        BIGINT,
    updated_by        BIGINT,
    CONSTRAINT ck_template_count CHECK (installment_count BETWEEN 1 AND 36),
    CONSTRAINT ck_template_days  CHECK (first_due_days BETWEEN 0 AND 365)
);

CREATE TABLE course_fee_template_charges (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    course_id   BIGINT         NOT NULL REFERENCES course_fee_templates (course_id) ON DELETE CASCADE,
    sort_order  INTEGER        NOT NULL,
    label       VARCHAR(120)   NOT NULL,
    amount      NUMERIC(12, 2) NOT NULL,
    due_days    INTEGER        NOT NULL DEFAULT 7,
    CONSTRAINT ck_template_charge_amount CHECK (amount > 0),
    CONSTRAINT ck_template_charge_days   CHECK (due_days BETWEEN 0 AND 365)
);
