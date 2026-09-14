-- A refund row is also its durable provider command. Unknown acceptance keeps its amount reserved.
ALTER TABLE payment_refunds
    ADD COLUMN provider_payment_intent_id VARCHAR(255),
    ADD COLUMN provider_idempotency_key VARCHAR(150),
    ADD COLUMN refund_request_id UUID,
    ADD COLUMN requested_by UUID,
    ADD COLUMN actor_type VARCHAR(40),
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN trace_id VARCHAR(128),
    ADD COLUMN requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN lease_until TIMESTAMPTZ,
    ADD COLUMN lease_token UUID,
    ADD COLUMN first_provider_attempt_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ;

UPDATE payment_refunds SET requested_at = created_at,
    first_provider_attempt_at = created_at,
    provider_idempotency_key = idempotency_key;
UPDATE payment_refunds r SET provider_payment_intent_id = (
    SELECT a.provider_payment_intent_id FROM payment_attempts a
    WHERE a.payment_id = r.payment_id AND a.status = 'SUCCESS'
    ORDER BY a.created_at DESC, a.id DESC LIMIT 1
);
ALTER TABLE payment_refunds DROP CONSTRAINT chk_payment_refunds_status;
ALTER TABLE payment_refunds ADD CONSTRAINT chk_payment_refunds_status CHECK (status IN (
    'REFUND_REQUESTED', 'REFUND_PROCESSING', 'REFUNDED', 'REFUND_FAILED', 'REFUND_MANUAL_REVIEW'
));
ALTER TABLE payment_refunds ADD CONSTRAINT chk_payment_refunds_attempt_count CHECK (attempt_count >= 0);
CREATE INDEX idx_payment_refunds_due ON payment_refunds(next_attempt_at, created_at, id)
    WHERE status IN ('REFUND_REQUESTED', 'REFUND_PROCESSING');
CREATE UNIQUE INDEX uk_payment_refunds_provider_idempotency ON payment_refunds(provider_idempotency_key)
    WHERE provider_idempotency_key IS NOT NULL;

CREATE TABLE payment_cancellation_requests (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    user_id UUID NOT NULL,
    requested_by UUID,
    actor_type VARCHAR(40),
    amount NUMERIC(19,2) NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL,
    reason TEXT,
    correlation_id VARCHAR(128),
    trace_id VARCHAR(128),
    expiry_requested BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(40) NOT NULL CHECK (status IN ('REQUESTED', 'WAITING_PROVIDER', 'COMPLETED', 'MANUAL_REVIEW')),
    failure_code VARCHAR(80),
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    requested_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_payment_cancellation_order ON payment_cancellation_requests(order_id);
CREATE INDEX idx_payment_cancellation_due ON payment_cancellation_requests(next_attempt_at, received_at, id)
    WHERE status IN ('REQUESTED', 'WAITING_PROVIDER');

ALTER TABLE payment_refunds ADD COLUMN last_reconciled_by UUID,
    ADD COLUMN last_reconciled_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_reason TEXT,
    ADD COLUMN reconciliation_count INTEGER NOT NULL DEFAULT 0;
