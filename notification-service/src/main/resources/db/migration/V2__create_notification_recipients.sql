CREATE TABLE notification_recipients (
    user_id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    active BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
