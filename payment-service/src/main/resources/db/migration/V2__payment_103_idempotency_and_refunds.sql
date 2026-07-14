ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS correlation_id VARCHAR(128);

ALTER TABLE payments
    ADD COLUMN IF NOT EXISTS trace_id VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_payments_correlation_id
    ON payments(correlation_id)
    WHERE correlation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payments_trace_id
    ON payments(trace_id)
    WHERE trace_id IS NOT NULL;

ALTER TABLE payment_attempts
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(150);

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_attempts_payment_id_idempotency_key
    ON payment_attempts(payment_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_attempts_provider_session
    ON payment_attempts(provider, provider_session_id)
    WHERE provider_session_id IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS ux_payment_attempts_provider_payment_intent
    ON payment_attempts(provider, provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_attempts_provider_charge
    ON payment_attempts(provider, provider_charge_id)
    WHERE provider_charge_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_attempts_expires_at
    ON payment_attempts(expires_at)
    WHERE expires_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_payment_refunds_payment_id_idempotency_key
    ON payment_refunds(payment_id, idempotency_key);