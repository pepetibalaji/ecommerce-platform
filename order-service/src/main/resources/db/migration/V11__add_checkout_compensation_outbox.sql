CREATE TABLE checkout_compensation_outbox (
 id UUID PRIMARY KEY, reservation_id UUID NOT NULL UNIQUE, product_id UUID NOT NULL, quantity INT NOT NULL,
 status VARCHAR(16) NOT NULL, attempt_count INT NOT NULL DEFAULT 0, next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
 last_error TEXT, CONSTRAINT ck_checkout_compensation_quantity CHECK (quantity > 0),
 CONSTRAINT ck_checkout_compensation_status CHECK (status IN ('PENDING','COMPLETED','FAILED'))
);
CREATE INDEX idx_checkout_compensation_pending ON checkout_compensation_outbox(status, next_attempt_at);
