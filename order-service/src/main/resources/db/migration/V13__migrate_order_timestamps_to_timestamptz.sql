-- All legacy timestamp-without-time-zone values were written by the Order Service as UTC.
-- Preserve their instant while making the timezone contract explicit for JDBC and API consumers.
ALTER TABLE orders
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN payment_confirmed_at TYPE TIMESTAMP WITH TIME ZONE USING payment_confirmed_at AT TIME ZONE 'UTC',
    ALTER COLUMN payment_failed_at TYPE TIMESTAMP WITH TIME ZONE USING payment_failed_at AT TIME ZONE 'UTC';

ALTER TABLE order_processed_events
    ALTER COLUMN processed_at TYPE TIMESTAMP WITH TIME ZONE USING processed_at AT TIME ZONE 'UTC';

ALTER TABLE order_inventory_release_outbox
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ALTER COLUMN completed_at TYPE TIMESTAMP WITH TIME ZONE USING completed_at AT TIME ZONE 'UTC',
    ALTER COLUMN next_attempt_at TYPE TIMESTAMP WITH TIME ZONE USING next_attempt_at AT TIME ZONE 'UTC';

ALTER TABLE order_created_outbox
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN next_attempt_at TYPE TIMESTAMP WITH TIME ZONE USING next_attempt_at AT TIME ZONE 'UTC',
    ALTER COLUMN published_at TYPE TIMESTAMP WITH TIME ZONE USING published_at AT TIME ZONE 'UTC';
