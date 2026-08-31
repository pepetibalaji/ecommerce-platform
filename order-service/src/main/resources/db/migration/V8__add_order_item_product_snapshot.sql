ALTER TABLE order_items ADD COLUMN product_name VARCHAR(255);
-- Existing historical rows have no catalog snapshot; retain them while all new orders write one.
