-- Bank reconciliation: the lines of the bank statement, each matched to the payment, expense or refund it belongs to, so
-- money the bank shows that the books do not (and the other way round) stands out.
CREATE TABLE bank_statement_lines (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    line_date    DATE           NOT NULL,
    description  VARCHAR(255)   NOT NULL DEFAULT '',
    reference    VARCHAR(80),
    debit        NUMERIC(12, 2) NOT NULL DEFAULT 0,
    credit       NUMERIC(12, 2) NOT NULL DEFAULT 0,
    import_key   VARCHAR(300)   NOT NULL,
    matched_kind VARCHAR(20),
    matched_id   BIGINT,
    matched_at   TIMESTAMPTZ,
    matched_by   BIGINT,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    created_by   BIGINT,
    updated_by   BIGINT,
    CONSTRAINT uk_bank_line_key UNIQUE (import_key),
    CONSTRAINT ck_bank_line_amount CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0)),
    CONSTRAINT ck_bank_line_kind CHECK (matched_kind IS NULL OR matched_kind IN ('PAYMENT', 'EXPENSE', 'REFUND')),
    CONSTRAINT ck_bank_line_match CHECK ((matched_kind IS NULL) = (matched_id IS NULL))
);

-- A payment, expense or refund can be the match of one bank line only.
CREATE UNIQUE INDEX uk_bank_line_target ON bank_statement_lines (matched_kind, matched_id) WHERE matched_kind IS NOT NULL;
CREATE INDEX ix_bank_line_date ON bank_statement_lines (line_date);
