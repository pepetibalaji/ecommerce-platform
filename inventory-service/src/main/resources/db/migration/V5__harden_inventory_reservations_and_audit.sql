ALTER TABLE inventory
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ADD CONSTRAINT ck_inventory_available_stock_non_negative CHECK (available_stock >= 0),
    ADD CONSTRAINT ck_inventory_reserved_stock_non_negative CHECK (reserved_stock >= 0);

ALTER TABLE inventory_reservations
    ALTER COLUMN created_at TYPE TIMESTAMP WITH TIME ZONE USING created_at AT TIME ZONE 'UTC',
    ALTER COLUMN updated_at TYPE TIMESTAMP WITH TIME ZONE USING updated_at AT TIME ZONE 'UTC',
    ADD COLUMN expires_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP + INTERVAL '15 minutes';
CREATE INDEX idx_inventory_reservations_expiry ON inventory_reservations(status, expires_at);

CREATE TABLE inventory_stock_ledger (
    id UUID PRIMARY KEY, product_id UUID NOT NULL, seller_id UUID, adjustment INT NOT NULL,
    previous_available_stock INT NOT NULL, new_available_stock INT NOT NULL,
    previous_reserved_stock INT NOT NULL, new_reserved_stock INT NOT NULL,
    reason VARCHAR(32) NOT NULL, actor_identity VARCHAR(128) NOT NULL,
    reference_id UUID, recorded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_inventory_stock_ledger_reason CHECK (reason IN
      ('STOCK_RECEIVED','STOCK_CORRECTION','DAMAGE','RETURN','MANUAL_RECONCILIATION')),
    CONSTRAINT ck_inventory_stock_ledger_available_non_negative CHECK (new_available_stock >= 0),
    CONSTRAINT ck_inventory_stock_ledger_reserved_non_negative CHECK (new_reserved_stock >= 0)
);

CREATE TABLE inventory_reservation_audit (
    id UUID PRIMARY KEY, reservation_id UUID NOT NULL, product_id UUID NOT NULL,
    from_status VARCHAR(20), to_status VARCHAR(20) NOT NULL,
    actor_identity VARCHAR(128) NOT NULL, recorded_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_inventory_reservation_audit_reservation ON inventory_reservation_audit(reservation_id, recorded_at);
