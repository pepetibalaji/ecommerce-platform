ALTER TABLE inventory
    ADD COLUMN product_active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN product_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN last_product_event_id UUID;

ALTER TABLE inventory ADD CONSTRAINT ck_inventory_product_version CHECK (product_version >= 0);
