CREATE TABLE payments (
    id UUID PRIMARY KEY,

    order_id UUID NOT NULL,
    user_id UUID NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    status VARCHAR(40) NOT NULL,
    provider VARCHAR(40) NOT NULL,

    idempotency_key VARCHAR(150) NOT NULL,
    failure_reason TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    version BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT uk_payments_order_id UNIQUE (order_id),
    CONSTRAINT uk_payments_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT chk_payments_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payments_currency_format CHECK (
        char_length(currency) = 3 AND currency = upper(currency)
    ),
    CONSTRAINT chk_payments_status CHECK (
        status IN (
            'PENDING',
            'REQUIRES_CUSTOMER_ACTION',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'CANCELLED',
            'REFUND_REQUESTED',
            'REFUND_PROCESSING',
            'REFUNDED',
            'REFUND_FAILED'
        )
    ),
    CONSTRAINT chk_payments_provider CHECK (
        provider IN (
            'STRIPE',
            'RAZORPAY',
            'SANDBOX'
        )
    )
);

CREATE INDEX idx_payments_user_id ON payments (user_id);
CREATE INDEX idx_payments_status ON payments (status);
CREATE INDEX idx_payments_provider ON payments (provider);
CREATE INDEX idx_payments_created_at ON payments (created_at);


CREATE TABLE payment_attempts (
    id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    provider VARCHAR(40) NOT NULL,

    provider_session_id VARCHAR(255),
    provider_payment_intent_id VARCHAR(255),
    provider_charge_id VARCHAR(255),

    checkout_url TEXT,

    status VARCHAR(40) NOT NULL,
    failure_reason TEXT,

    expires_at TIMESTAMP,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_attempts_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE CASCADE,

    CONSTRAINT chk_payment_attempts_provider CHECK (
        provider IN (
            'STRIPE',
            'RAZORPAY',
            'SANDBOX'
        )
    ),
    CONSTRAINT chk_payment_attempts_status CHECK (
        status IN (
            'CREATED',
            'REQUIRES_CUSTOMER_ACTION',
            'PROCESSING',
            'SUCCESS',
            'FAILED',
            'CANCELLED',
            'EXPIRED'
        )
    )
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts (payment_id);
CREATE INDEX idx_payment_attempts_status ON payment_attempts (status);
CREATE INDEX idx_payment_attempts_provider ON payment_attempts (provider);
CREATE INDEX idx_payment_attempts_created_at ON payment_attempts (created_at);

CREATE UNIQUE INDEX uk_payment_attempts_provider_session_id
    ON payment_attempts (provider_session_id)
    WHERE provider_session_id IS NOT NULL;

CREATE UNIQUE INDEX uk_payment_attempts_provider_payment_intent_id
    ON payment_attempts (provider_payment_intent_id)
    WHERE provider_payment_intent_id IS NOT NULL;

CREATE UNIQUE INDEX uk_payment_attempts_provider_charge_id
    ON payment_attempts (provider_charge_id)
    WHERE provider_charge_id IS NOT NULL;


CREATE TABLE payment_refunds (
    id UUID PRIMARY KEY,

    payment_id UUID NOT NULL,

    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,

    provider_refund_id VARCHAR(255),

    status VARCHAR(40) NOT NULL,

    reason TEXT,
    failure_reason TEXT,

    idempotency_key VARCHAR(150) NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_refunds_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE CASCADE,

    CONSTRAINT uk_payment_refunds_idempotency_key UNIQUE (idempotency_key),

    CONSTRAINT chk_payment_refunds_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_payment_refunds_currency_format CHECK (
        char_length(currency) = 3 AND currency = upper(currency)
    ),
    CONSTRAINT chk_payment_refunds_status CHECK (
        status IN (
            'REFUND_REQUESTED',
            'REFUND_PROCESSING',
            'REFUNDED',
            'REFUND_FAILED'
        )
    )
);

CREATE INDEX idx_payment_refunds_payment_id ON payment_refunds (payment_id);
CREATE INDEX idx_payment_refunds_status ON payment_refunds (status);
CREATE INDEX idx_payment_refunds_created_at ON payment_refunds (created_at);

CREATE UNIQUE INDEX uk_payment_refunds_provider_refund_id
    ON payment_refunds (provider_refund_id)
    WHERE provider_refund_id IS NOT NULL;


CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,

    provider VARCHAR(40) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,

    payment_id UUID,

    event_type VARCHAR(150) NOT NULL,
    processing_status VARCHAR(40) NOT NULL,

    payload_hash VARCHAR(128),

    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,

    CONSTRAINT fk_payment_webhook_events_payment
        FOREIGN KEY (payment_id)
        REFERENCES payments (id)
        ON DELETE SET NULL,

    CONSTRAINT uk_payment_webhook_events_provider_event
        UNIQUE (provider, provider_event_id),

    CONSTRAINT chk_payment_webhook_events_provider CHECK (
        provider IN (
            'STRIPE',
            'RAZORPAY',
            'SANDBOX'
        )
    ),
    CONSTRAINT chk_payment_webhook_events_processing_status CHECK (
        processing_status IN (
            'RECEIVED',
            'PROCESSED',
            'IGNORED',
            'FAILED'
        )
    )
);

CREATE INDEX idx_payment_webhook_events_payment_id ON payment_webhook_events (payment_id);
CREATE INDEX idx_payment_webhook_events_processing_status ON payment_webhook_events (processing_status);
CREATE INDEX idx_payment_webhook_events_received_at ON payment_webhook_events (received_at);