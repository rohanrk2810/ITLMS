-- =====================================================================
-- Online course purchases through Razorpay.
--
-- One row per checkout. The row, not the browser, decides what was bought and for how much: the amount is fixed
-- when the order is created, and a payment is accepted only when Razorpay's signature over (order id | payment id)
-- checks out. CREATED -> PAID (money received, fee plan and payment recorded) -> ENROLLED (placed in a batch).
-- A PAID order that never reaches ENROLLED needs the institute to place the student; the money is not lost.
-- =====================================================================

CREATE TABLE online_orders (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    razorpay_order_id   VARCHAR(80)    NOT NULL,
    razorpay_payment_id VARCHAR(80),

    student_id          BIGINT         NOT NULL,
    student_user_id     BIGINT,
    course_id           BIGINT         NOT NULL,
    course_title        VARCHAR(200),
    batch_id            BIGINT,

    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3)     NOT NULL DEFAULT 'INR',

    status              VARCHAR(12)    NOT NULL DEFAULT 'CREATED',
    fee_plan_id         BIGINT,
    payment_id          BIGINT,
    paid_at             TIMESTAMPTZ,

    created_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by          BIGINT,
    updated_by          BIGINT,

    CONSTRAINT uk_online_order_rzp UNIQUE (razorpay_order_id),
    CONSTRAINT ck_online_order_status CHECK (status IN ('CREATED', 'PAID', 'ENROLLED')),
    CONSTRAINT ck_online_order_amount CHECK (amount > 0)
);

CREATE INDEX ix_online_order_student ON online_orders (student_id);
