-- GST settings the administrator sets in the portal: the rate, the institute's GSTIN and SAC, and from which date the rate
-- applies. Payments, receipts and books dated before that date are left as they were, so switching GST on does not
-- rewrite history. One row; until it is saved, the deployment settings (GST_PERCENT and friends) are used.
CREATE TABLE gst_settings (
    id             SMALLINT      PRIMARY KEY DEFAULT 1,
    percent        NUMERIC(5, 2) NOT NULL DEFAULT 0,
    gstin          VARCHAR(15),
    sac_code       VARCHAR(10),
    effective_from DATE,
    set_by         BIGINT,
    set_at         TIMESTAMPTZ,
    CONSTRAINT ck_gst_single CHECK (id = 1),
    CONSTRAINT ck_gst_percent CHECK (percent >= 0 AND percent <= 28)
);
INSERT INTO gst_settings (id) VALUES (1);
