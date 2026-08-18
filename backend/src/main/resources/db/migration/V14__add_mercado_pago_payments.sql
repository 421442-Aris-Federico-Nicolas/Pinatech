CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL,
    order_id BIGINT NOT NULL REFERENCES customer_orders(id),
    provider VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    preference_id VARCHAR(100),
    provider_payment_id VARCHAR(100),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    checkout_url TEXT,
    expires_at TIMESTAMPTZ NOT NULL,
    refund_idempotency_key UUID,
    refund_id VARCHAR(100),
    last_provider_status VARCHAR(50),
    last_error VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_payment_attempts_public_id UNIQUE (public_id),
    CONSTRAINT uq_payment_attempts_order_idempotency UNIQUE (order_id, idempotency_key),
    CONSTRAINT chk_payment_attempts_provider CHECK (provider IN ('MERCADO_PAGO')),
    CONSTRAINT chk_payment_attempts_status CHECK (status IN (
        'CREATED', 'PREFERENCE_CREATED', 'PENDING', 'REJECTED', 'APPROVED',
        'REFUND_PENDING', 'REFUNDED'
    )),
    CONSTRAINT chk_payment_attempts_currency CHECK (currency = 'ARS')
);

CREATE UNIQUE INDEX uq_payment_attempts_preference_id
    ON payment_attempts(preference_id) WHERE preference_id IS NOT NULL;
CREATE UNIQUE INDEX uq_payment_attempts_provider_payment_id
    ON payment_attempts(provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX idx_payment_attempts_order_id ON payment_attempts(order_id);
CREATE INDEX idx_payment_attempts_refund_pending
    ON payment_attempts(updated_at, id) WHERE status = 'REFUND_PENDING';

ALTER TABLE customer_orders
    DROP CONSTRAINT chk_customer_orders_payment_status,
    ADD CONSTRAINT chk_customer_orders_payment_status CHECK (payment_status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED',
        'REFUND_PENDING', 'REFUNDED'
    ));

CREATE TABLE payment_events (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES payment_attempts(id),
    provider_payment_id VARCHAR(100) NOT NULL,
    request_id VARCHAR(100) NOT NULL,
    event_key VARCHAR(64) NOT NULL,
    provider_status VARCHAR(50) NOT NULL,
    provider_status_detail VARCHAR(100),
    notification_payload_hash VARCHAR(64) NOT NULL,
    provider_payload_hash VARCHAR(64) NOT NULL,
    outcome VARCHAR(50) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMPTZ,
    CONSTRAINT uq_payment_events_event_key UNIQUE (event_key)
);

CREATE INDEX idx_payment_events_attempt_id ON payment_events(attempt_id, received_at);
CREATE INDEX idx_payment_events_provider_payment_id ON payment_events(provider_payment_id);
CREATE INDEX idx_payment_events_request_id ON payment_events(request_id);
