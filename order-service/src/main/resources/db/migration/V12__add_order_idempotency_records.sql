CREATE TABLE order_idempotency_records (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    order_id UUID,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_order_idempotency_records_user_key UNIQUE (user_id, idempotency_key),
    CONSTRAINT ck_order_idempotency_records_status CHECK (status IN ('PROCESSING', 'COMPLETED'))
);

CREATE INDEX idx_order_idempotency_records_expires_at
    ON order_idempotency_records (expires_at);

CREATE UNIQUE INDEX uk_order_idempotency_records_order_id
    ON order_idempotency_records (order_id)
    WHERE order_id IS NOT NULL;
