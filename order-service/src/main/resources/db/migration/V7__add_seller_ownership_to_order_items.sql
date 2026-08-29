ALTER TABLE order_items ADD COLUMN seller_id UUID;

-- Existing orders pre-date seller ownership. They intentionally remain unavailable to
-- seller views until an administrator performs a controlled backfill from the catalog.
CREATE INDEX idx_order_items_seller_id ON order_items(seller_id);
