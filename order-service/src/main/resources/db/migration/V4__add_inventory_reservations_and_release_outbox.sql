ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS inventory_reservation_id UUID;

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_items_inventory_reservation_id
    ON order_items(inventory_reservation_id)
    WHERE inventory_reservation_id IS NOT NULL;

CREATE TABLE order_inventory_release_outbox (
    id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
    order_item_id UUID NOT NULL REFERENCES order_items(id) ON DELETE CASCADE,
    reservation_id UUID NOT NULL,
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    last_error TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    completed_at TIMESTAMP,
    CONSTRAINT ck_order_inventory_release_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_order_inventory_release_reason CHECK (reason IN ('PAYMENT_FAILED', 'CANCELLED')),
    CONSTRAINT ck_order_inventory_release_status CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT uk_order_inventory_release_reservation UNIQUE (reservation_id)
);

CREATE INDEX idx_order_inventory_release_pending
    ON order_inventory_release_outbox(status, created_at);

CREATE INDEX idx_order_inventory_release_order
    ON order_inventory_release_outbox(order_id);
