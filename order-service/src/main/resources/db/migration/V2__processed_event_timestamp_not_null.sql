-- Make processing timestamps mandatory.
-- Existing rows must be checked before applying this migration.

ALTER TABLE processed_event
    MODIFY COLUMN processed_at DATETIME(6) NOT NULL;