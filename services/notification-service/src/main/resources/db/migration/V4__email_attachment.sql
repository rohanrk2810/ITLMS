-- An email may carry one attachment (a payment receipt PDF), and may be tied to the event that caused it so a
-- redelivered event does not queue the same email twice.
ALTER TABLE email_outbox ADD COLUMN attachment_name VARCHAR(120);
ALTER TABLE email_outbox ADD COLUMN attachment BYTEA;
ALTER TABLE email_outbox ADD COLUMN event_id VARCHAR(60);

CREATE UNIQUE INDEX uk_outbox_event ON email_outbox (event_id) WHERE event_id IS NOT NULL;
