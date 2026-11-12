-- A fee head can be marked as carrying no GST (a refundable deposit, an exempt service). Payments towards it are reported
-- as exempt and are not taxed on the receipt.
ALTER TABLE fee_installments ADD COLUMN gst_exempt BOOLEAN NOT NULL DEFAULT FALSE;
