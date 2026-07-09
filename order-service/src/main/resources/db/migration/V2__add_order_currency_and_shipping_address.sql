ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE orders
SET currency = 'INR'
WHERE currency IS NULL OR btrim(currency) = '';

ALTER TABLE orders
    ALTER COLUMN currency SET NOT NULL;

ALTER TABLE orders
    ADD CONSTRAINT ck_orders_currency_format
    CHECK (currency ~ '^[A-Z]{3}$');

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

UPDATE orders
SET updated_at = created_at
WHERE updated_at IS NULL;

ALTER TABLE orders
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_address_id UUID;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_recipient_name VARCHAR(150);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_phone VARCHAR(30);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_line1 VARCHAR(255);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_line2 VARCHAR(255);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_city VARCHAR(100);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_state VARCHAR(100);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_postal_code VARCHAR(30);

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS shipping_country VARCHAR(2);

CREATE INDEX IF NOT EXISTS idx_orders_currency
    ON orders(currency);

CREATE INDEX IF NOT EXISTS idx_orders_shipping_address_id
    ON orders(shipping_address_id)
    WHERE shipping_address_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_orders_updated_at
    ON orders(updated_at);