ALTER TABLE order_inventory_release_outbox
    DROP CONSTRAINT IF EXISTS ck_order_inventory_release_reason;
ALTER TABLE order_inventory_release_outbox
    ADD CONSTRAINT ck_order_inventory_release_reason
        CHECK (reason IN ('PAYMENT_FAILED', 'CANCELLED', 'FULL_REFUND'));
ALTER TABLE order_inventory_release_outbox
    DROP CONSTRAINT IF EXISTS ck_order_inventory_release_status;
ALTER TABLE order_inventory_release_outbox
    ADD CONSTRAINT ck_order_inventory_release_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'MANUAL_REVIEW'));
