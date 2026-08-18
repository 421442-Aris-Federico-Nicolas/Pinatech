ALTER TABLE payment_attempts
    ADD COLUMN reconciliation_next_retry_at TIMESTAMPTZ,
    ADD COLUMN reconciliation_attempt_count INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN reconciliation_lease_until TIMESTAMPTZ;

CREATE TABLE provider_payments (
    id BIGSERIAL PRIMARY KEY,
    attempt_id BIGINT NOT NULL REFERENCES payment_attempts(id),
    provider_payment_id VARCHAR(100) NOT NULL,
    provider_status VARCHAR(50) NOT NULL,
    provider_status_detail VARCHAR(100),
    amount NUMERIC(19, 2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    approved_at TIMESTAMPTZ,
    provider_updated_at TIMESTAMPTZ,
    funds_order BOOLEAN NOT NULL DEFAULT FALSE,
    live_mode BOOLEAN NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    amount_refunded NUMERIC(19, 2) NOT NULL DEFAULT 0,
    dispute_status VARCHAR(30),
    refund_idempotency_key UUID,
    refund_id VARCHAR(100),
    refund_status VARCHAR(30),
    refund_amount NUMERIC(19, 2),
    refund_last_error VARCHAR(500),
    next_retry_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    lease_until TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_provider_payments_provider_payment_id UNIQUE (provider_payment_id),
    CONSTRAINT chk_provider_payments_currency CHECK (currency = 'ARS'),
    CONSTRAINT chk_provider_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_provider_payments_amount_refunded CHECK (
        amount_refunded >= 0 AND amount_refunded <= amount
    ),
    CONSTRAINT chk_provider_payments_refund_amount CHECK (
        refund_amount IS NULL OR (refund_amount > 0 AND refund_amount <= amount)
    ),
    CONSTRAINT chk_provider_payments_refund_status CHECK (
        refund_status IS NULL OR refund_status IN ('PENDING', 'APPROVED', 'REJECTED')
    ),
    CONSTRAINT chk_provider_payments_dispute_status CHECK (
        dispute_status IS NULL OR dispute_status IN ('MEDIATION', 'CHARGEBACK')
    )
);

-- Preserve every provider ID already recorded by V14. If old data contains more
-- than one approved attempt, only the earliest one is considered the financier.
WITH legacy AS (
    SELECT attempt.*,
           ROW_NUMBER() OVER (
               PARTITION BY attempt.order_id, attempt.status
               ORDER BY attempt.created_at, attempt.id
           ) AS approved_position
    FROM payment_attempts attempt
    WHERE attempt.provider_payment_id IS NOT NULL
)
INSERT INTO provider_payments (
    attempt_id, provider_payment_id, provider_status, amount, currency,
    funds_order, live_mode, operation_type, amount_refunded,
    refund_idempotency_key, refund_id, refund_status, refund_amount,
    refund_last_error, next_retry_at, created_at, updated_at
)
SELECT id,
       provider_payment_id,
       COALESCE(last_provider_status, LOWER(status)),
       amount,
       currency,
       status = 'APPROVED' AND approved_position = 1,
       FALSE,
       'regular_payment',
       CASE WHEN status = 'REFUNDED' THEN amount ELSE 0 END,
       refund_idempotency_key,
       refund_id,
       CASE
           WHEN status = 'REFUNDED' THEN 'APPROVED'
           WHEN status = 'REFUND_PENDING' THEN 'PENDING'
           ELSE NULL
       END,
       CASE WHEN status IN ('REFUND_PENDING', 'REFUNDED') THEN amount ELSE NULL END,
       last_error,
       CASE WHEN status = 'REFUND_PENDING' THEN CURRENT_TIMESTAMP ELSE NULL END,
       created_at,
       updated_at
FROM legacy
ON CONFLICT (provider_payment_id) DO NOTHING;

DROP INDEX IF EXISTS uq_payment_attempts_provider_payment_id;

-- V14 did not prevent several live preferences for one order. Keep the newest
-- attempt active and retire the rest before installing the production guard.
WITH duplicates AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY order_id
               ORDER BY updated_at DESC, id DESC
           ) AS position
    FROM payment_attempts
    WHERE status IN ('CREATED', 'PREFERENCE_CREATED', 'PENDING')
)
UPDATE payment_attempts attempt
SET status = 'REJECTED',
    last_error = COALESCE(attempt.last_error, 'Superseded while enforcing one active preference per order.'),
    updated_at = CURRENT_TIMESTAMP
FROM duplicates duplicate
WHERE attempt.id = duplicate.id
  AND duplicate.position > 1;

CREATE UNIQUE INDEX uq_payment_attempts_one_active_per_order
    ON payment_attempts(order_id)
    WHERE status IN ('CREATED', 'PREFERENCE_CREATED', 'PENDING');

CREATE INDEX idx_payment_attempts_reconciliation_due
    ON payment_attempts(reconciliation_next_retry_at, id)
    WHERE preference_id IS NOT NULL;

CREATE INDEX idx_provider_payments_attempt_id
    ON provider_payments(attempt_id, id);

CREATE INDEX idx_provider_payments_refund_due
    ON provider_payments(next_retry_at, id)
    WHERE refund_status IN ('PENDING', 'REJECTED');

ALTER TABLE customer_orders
    DROP CONSTRAINT chk_customer_orders_payment_status,
    ADD CONSTRAINT chk_customer_orders_payment_status CHECK (payment_status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED',
        'REFUND_PENDING', 'REFUNDED', 'IN_MEDIATION', 'CHARGEBACK'
    ));
