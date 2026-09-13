CREATE TABLE inventory_event_outbox (
    id UUID PRIMARY KEY,
    topic VARCHAR(128) NOT NULL,
    message_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE,
    lease_until TIMESTAMP WITH TIME ZONE,
    published_at TIMESTAMP WITH TIME ZONE,
    last_error VARCHAR(128),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_inventory_outbox_status CHECK (status IN ('PENDING','PROCESSING','PUBLISHED','DEAD'))
);
CREATE INDEX idx_inventory_event_outbox_due ON inventory_event_outbox(status, next_attempt_at, created_at);
