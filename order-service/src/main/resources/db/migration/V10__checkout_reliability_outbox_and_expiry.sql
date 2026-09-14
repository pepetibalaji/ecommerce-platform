ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_request_hash VARCHAR(64);
ALTER TABLE orders ADD COLUMN IF NOT EXISTS idempotency_expires_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE orders ADD COLUMN IF NOT EXISTS payment_expires_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE order_created_outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);
CREATE INDEX idx_order_created_outbox_pending ON order_created_outbox(status, next_attempt_at, created_at);

ALTER TABLE order_inventory_release_outbox DROP CONSTRAINT IF EXISTS ck_order_inventory_release_reason;
ALTER TABLE order_inventory_release_outbox ADD CONSTRAINT ck_order_inventory_release_reason CHECK (reason IN ('PAYMENT_FAILED', 'CANCELLED', 'FULL_REFUND', 'PAYMENT_EXPIRED'));
