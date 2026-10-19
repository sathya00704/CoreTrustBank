-- The publisher only ever looks for unsent events. A partial index covers just those rows,
-- so it stays tiny however many published events accumulate.
DROP INDEX IF EXISTS idx_outbox_events_unpublished;

CREATE INDEX idx_outbox_events_pending
    ON outbox_events (id)
    WHERE published_at IS NULL;