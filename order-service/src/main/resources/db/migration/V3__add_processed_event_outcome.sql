-- CSA-04A.10
-- Record the business outcome of processed Kafka events.
--
-- Historical rows retain NULL because their actual
-- processing outcomes cannot be reconstructed reliably.

ALTER TABLE processed_event
    ADD COLUMN outcome VARCHAR(40) NULL;