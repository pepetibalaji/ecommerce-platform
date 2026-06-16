ALTER TABLE refresh_tokens
DROP CONSTRAINT IF EXISTS uk7tdcd6ab5wsgoudnvj7xf1b7l;

DROP INDEX IF EXISTS idx_refresh_tokens_user_id;

CREATE INDEX idx_refresh_tokens_user_id
    ON refresh_tokens(user_id);