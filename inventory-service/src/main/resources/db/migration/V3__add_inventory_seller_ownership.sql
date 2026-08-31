ALTER TABLE inventory ADD COLUMN seller_id UUID;
CREATE INDEX idx_inventory_seller_id ON inventory(seller_id);
