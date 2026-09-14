-- A confirmed order is never directly cancelled. It enters REFUND_REQUESTED and this durable
-- command is retried until the Payment Service has accepted it or operations intervene.
CREATE TABLE order_refund_request_outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL UNIQUE REFERENCES orders(id) ON DELETE CASCADE,
    payment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    requested_by UUID,
    actor_type VARCHAR(32) NOT NULL,
    amount DECIMAL(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason TEXT,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    last_error TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_order_refund_request_status CHECK (status IN ('PENDING', 'PUBLISHED', 'FAILED')),
    CONSTRAINT ck_order_refund_request_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_order_refund_request_currency CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE INDEX idx_order_refund_request_outbox_pending
    ON order_refund_request_outbox(status, next_attempt_at, created_at);

-- Audit entries are append-only at the application layer. They include customer, admin, and
-- payment-system decisions so a support investigation does not rely on log retention.
CREATE TABLE order_lifecycle_audit (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    action VARCHAR(64) NOT NULL,
    actor_id UUID,
    actor_type VARCHAR(32) NOT NULL,
    reason TEXT,
    refund_request_id UUID REFERENCES order_refund_request_outbox(id) ON DELETE SET NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_order_lifecycle_audit_order_created
    ON order_lifecycle_audit(order_id, created_at);
