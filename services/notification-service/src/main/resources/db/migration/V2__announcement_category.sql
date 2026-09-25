-- What an announcement is about (course, batch, live class, test, assignment, institute
-- information), separate from who it reaches. Existing rows are plain GENERAL notices.
ALTER TABLE announcements
    ADD COLUMN category VARCHAR(20) NOT NULL DEFAULT 'GENERAL';

ALTER TABLE announcements
    ADD CONSTRAINT ck_announcement_category CHECK (category IN
        ('GENERAL', 'COURSE', 'BATCH', 'LIVE_CLASS', 'TEST', 'ASSIGNMENT', 'INSTITUTE'));
