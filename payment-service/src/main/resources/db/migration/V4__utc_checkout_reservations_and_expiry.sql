-- Historical timestamp-without-time-zone values were written in UTC. Preserve that interpretation.
ALTER TABLE payments
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN last_provider_check_at TYPE TIMESTAMPTZ USING last_provider_check_at AT TIME ZONE 'UTC';
ALTER TABLE payment_attempts
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN expires_at TYPE TIMESTAMPTZ USING expires_at AT TIME ZONE 'UTC',
    ADD COLUMN success_url TEXT,
    ADD COLUMN cancel_url TEXT;
ALTER TABLE payment_refunds
    ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMPTZ USING updated_at AT TIME ZONE 'UTC';
ALTER TABLE payment_webhook_events
    ALTER COLUMN received_at TYPE TIMESTAMPTZ USING received_at AT TIME ZONE 'UTC',
    ALTER COLUMN processed_at TYPE TIMESTAMPTZ USING processed_at AT TIME ZONE 'UTC';

UPDATE payment_attempts SET idempotency_key = 'legacy-checkout:' || id::text
 WHERE idempotency_key IS NULL OR btrim(idempotency_key) = '';
ALTER TABLE payment_attempts ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE payments DROP CONSTRAINT chk_payments_status;
ALTER TABLE payments ADD CONSTRAINT chk_payments_status CHECK (status IN (
 'PENDING', 'REQUIRES_CUSTOMER_ACTION', 'PROCESSING', 'SUCCESS', 'FAILED', 'CANCELLED', 'EXPIRED',
 'REFUND_REQUESTED', 'REFUND_PROCESSING', 'REFUNDED', 'REFUND_FAILED'));

-- Refuse deployment if existing duplicate active sessions require operator reconciliation.
CREATE UNIQUE INDEX ux_payment_attempts_one_active ON payment_attempts(payment_id)
 WHERE status IN ('CREATED', 'REQUIRES_CUSTOMER_ACTION', 'PROCESSING');
