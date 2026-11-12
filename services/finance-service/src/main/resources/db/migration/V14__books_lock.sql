-- Closing the books. Once the administrator closes the books up to a date, nothing dated on or before it can be recorded,
-- reversed or voided: payments, expenses, refunds paid out, salary paid and cash counts. One row; the date only moves
-- on the administrator's say-so, and every move is audited.
CREATE TABLE books_lock (
    id             SMALLINT    PRIMARY KEY DEFAULT 1,
    locked_through DATE,
    note           VARCHAR(255),
    set_by         BIGINT,
    set_at         TIMESTAMPTZ,
    CONSTRAINT ck_books_lock_single CHECK (id = 1)
);
INSERT INTO books_lock (id) VALUES (1);
