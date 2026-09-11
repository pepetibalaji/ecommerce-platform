ALTER TABLE refresh_sessions ADD COLUMN session_started_at TIMESTAMPTZ;
ALTER TABLE refresh_sessions ADD COLUMN idle_expires_at TIMESTAMPTZ;

-- Existing sessions receive one final 30-minute idle window after migration.
UPDATE refresh_sessions
SET session_started_at = created_at,
    idle_expires_at = LEAST(expires_at, NOW() + INTERVAL '30 minutes')
WHERE session_started_at IS NULL OR idle_expires_at IS NULL;

ALTER TABLE refresh_sessions ALTER COLUMN session_started_at SET NOT NULL;
ALTER TABLE refresh_sessions ALTER COLUMN idle_expires_at SET NOT NULL;

ALTER TABLE refresh_sessions
    ADD CONSTRAINT chk_refresh_session_idle_expiry
    CHECK (session_started_at <= idle_expires_at AND idle_expires_at <= expires_at);

CREATE INDEX idx_refresh_sessions_idle_expiry
    ON refresh_sessions(idle_expires_at)
    WHERE revoked_at IS NULL;
