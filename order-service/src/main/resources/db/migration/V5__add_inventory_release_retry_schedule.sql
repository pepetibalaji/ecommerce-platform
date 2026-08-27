ALTER TABLE order_inventory_release_outbox
    ADD COLUMN next_attempt_at TIMESTAMP;

UPDATE order_inventory_release_outbox
SET next_attempt_at = COALESCE(updated_at, created_at);

ALTER TABLE order_inventory_release_outbox
    ALTER COLUMN next_attempt_at SET NOT NULL;

ALTER TABLE order_inventory_release_outbox
    DROP CONSTRAINT ck_order_inventory_release_status,
    ADD CONSTRAINT ck_order_inventory_release_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED'));

DROP INDEX IF EXISTS idx_order_inventory_release_pending;

CREATE INDEX idx_order_inventory_release_pending
    ON order_inventory_release_outbox(status, next_attempt_at, created_at);
