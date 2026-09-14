ALTER TABLE order_refund_request_outbox ALTER COLUMN payment_id DROP NOT NULL;
ALTER TABLE order_refund_request_outbox ADD COLUMN command_type VARCHAR(16) NOT NULL DEFAULT 'REFUND';
ALTER TABLE order_refund_request_outbox ADD COLUMN correlation_id VARCHAR(255);
ALTER TABLE order_refund_request_outbox ADD COLUMN trace_id VARCHAR(255);
ALTER TABLE order_refund_request_outbox ADD CONSTRAINT ck_order_payment_command_type CHECK (command_type IN ('REFUND', 'CANCELLATION', 'EXPIRY'));
-- Outcomes reference provider refund/event IDs, not necessarily the original request outbox UUID.
ALTER TABLE order_lifecycle_audit DROP CONSTRAINT IF EXISTS order_lifecycle_audit_refund_request_id_fkey;
ALTER TABLE order_refund_request_outbox DROP CONSTRAINT order_refund_request_outbox_order_id_key;
CREATE UNIQUE INDEX uk_order_payment_command_type ON order_refund_request_outbox(order_id, command_type);
