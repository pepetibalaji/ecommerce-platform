ALTER TABLE auth_outbox_events
    ADD COLUMN next_attempt_at TIMESTAMPTZ,
    ADD COLUMN lease_owner VARCHAR(128),
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN dead_lettered_at TIMESTAMPTZ;

CREATE INDEX idx_auth_outbox_events_claimable
    ON auth_outbox_events (created_at)
    WHERE published_at IS NULL AND dead_lettered_at IS NULL;
