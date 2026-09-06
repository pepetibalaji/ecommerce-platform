ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS idempotency_key VARCHAR(100);

CREATE UNIQUE INDEX IF NOT EXISTS uk_orders_user_idempotency_key
    ON orders(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
