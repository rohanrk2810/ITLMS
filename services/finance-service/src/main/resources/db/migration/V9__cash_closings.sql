-- The day-end cash count. The desk counts the cash in the drawer, the system says how much cash it recorded that day, and
-- the difference is kept with who closed the day. One closing per day.
CREATE TABLE cash_closings (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    closing_date  DATE           NOT NULL,
    expected_cash NUMERIC(12, 2) NOT NULL,
    counted_cash  NUMERIC(12, 2) NOT NULL,
    difference    NUMERIC(12, 2) NOT NULL,
    note          VARCHAR(255),
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by    BIGINT,
    updated_by    BIGINT,
    CONSTRAINT uk_cash_closing_date UNIQUE (closing_date),
    CONSTRAINT ck_cash_counted CHECK (counted_cash >= 0),
    CONSTRAINT ck_cash_difference CHECK (difference = counted_cash - expected_cash)
);
