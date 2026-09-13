ALTER TABLE inventory ADD COLUMN low_stock_event_level VARCHAR(16);
ALTER TABLE inventory ADD COLUMN low_stock_event_at TIMESTAMP WITH TIME ZONE;
