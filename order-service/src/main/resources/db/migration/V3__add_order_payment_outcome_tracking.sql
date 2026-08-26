ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS payment_id UUID,
    ADD COLUMN IF NOT EXISTS payment_confirmed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_failed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payment_failure_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_orders_payment_id
    ON orders(payment_id)
    WHERE payment_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS order_processed_events (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    processed_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_order_processed_events_event_id UNIQUE (event_id)
);

CREATE INDEX IF NOT EXISTS idx_order_processed_events_order_id
    ON order_processed_events(order_id);
