CREATE TABLE inventory_reservations (
    id UUID PRIMARY KEY,
    product_id UUID NOT NULL,
    quantity INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT ck_inventory_reservations_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_inventory_reservations_status
        CHECK (status IN ('RESERVED', 'RELEASED', 'DEDUCTED'))
);

CREATE INDEX idx_inventory_reservations_product_id
    ON inventory_reservations(product_id);
