CREATE TABLE payment_event_outbox (
    id UUID PRIMARY KEY,
    sequence BIGSERIAL UNIQUE NOT NULL,
    outcome_key VARCHAR(180) UNIQUE NOT NULL,
    payment_id UUID,
    order_id UUID NOT NULL,
    topic VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','LEASED','DELIVERED','DEAD')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    last_error_code VARCHAR(80),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    delivered_at TIMESTAMPTZ
);
CREATE INDEX idx_payment_outbox_due ON payment_event_outbox(status, next_attempt_at, sequence);
CREATE INDEX idx_payment_outbox_order ON payment_event_outbox(order_id, sequence) WHERE status <> 'DELIVERED';

ALTER TABLE payment_webhook_events ADD COLUMN verified_metadata TEXT;
ALTER TABLE payment_webhook_events ADD COLUMN attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE payment_webhook_events ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now();
ALTER TABLE payment_webhook_events ADD COLUMN last_error_code VARCHAR(80);
CREATE INDEX idx_payment_webhook_retry ON payment_webhook_events(processing_status, next_attempt_at);

-- Shared admission control across replicas; bodies are never stored here.
CREATE TABLE payment_webhook_rate_limit (
    provider VARCHAR(40) PRIMARY KEY,
    window_start TIMESTAMPTZ NOT NULL,
    request_count INTEGER NOT NULL
);
