CREATE TABLE guest_checkout_sessions (
    id UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    csrf_hash VARCHAR(64) NOT NULL CHECK (csrf_hash ~ '^[0-9a-f]{64}$'),
    verified_email VARCHAR(254),
    email_verified_at TIMESTAMPTZ,
    challenge_email VARCHAR(254),
    challenge_hash VARCHAR(255),
    challenge_expires_at TIMESTAMPTZ,
    challenge_attempts INTEGER NOT NULL DEFAULT 0 CHECK (challenge_attempts BETWEEN 0 AND 10),
    challenge_requested_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    CONSTRAINT chk_guest_checkout_email_verification CHECK (
        (verified_email IS NULL AND email_verified_at IS NULL)
        OR (verified_email IS NOT NULL AND email_verified_at IS NOT NULL)
    ),
    CONSTRAINT chk_guest_checkout_challenge CHECK (
        (challenge_email IS NULL AND challenge_hash IS NULL AND challenge_expires_at IS NULL)
        OR (challenge_email IS NOT NULL AND challenge_hash IS NOT NULL AND challenge_expires_at IS NOT NULL)
    )
);
CREATE INDEX idx_guest_checkout_sessions_expiry ON guest_checkout_sessions(expires_at);

ALTER TABLE customer_orders
    ADD COLUMN public_id UUID,
    ADD COLUMN guest_session_id UUID REFERENCES guest_checkout_sessions(id) ON DELETE RESTRICT,
    ADD COLUMN guest_access_token_hash VARCHAR(64),
    ADD COLUMN buyer_first_name VARCHAR(100),
    ADD COLUMN buyer_last_name VARCHAR(100),
    ADD COLUMN buyer_email VARCHAR(254),
    ADD COLUMN buyer_phone VARCHAR(50),
    ADD COLUMN buyer_document_number VARCHAR(50),
    ALTER COLUMN user_id DROP NOT NULL;

UPDATE customer_orders customer_order
SET public_id = gen_random_uuid(),
    buyer_first_name = customer.first_name,
    buyer_last_name = customer.last_name,
    buyer_email = lower(customer.email),
    buyer_phone = COALESCE(NULLIF(trim(customer.phone), ''), 'N/A'),
    buyer_document_number = COALESCE(NULLIF(trim(customer.document_number), ''), 'N/A')
FROM users customer
WHERE customer.id = customer_order.user_id;

ALTER TABLE customer_orders
    ALTER COLUMN public_id SET NOT NULL,
    ALTER COLUMN buyer_first_name SET NOT NULL,
    ALTER COLUMN buyer_last_name SET NOT NULL,
    ALTER COLUMN buyer_email SET NOT NULL,
    ALTER COLUMN buyer_phone SET NOT NULL,
    ALTER COLUMN buyer_document_number SET NOT NULL,
    ADD CONSTRAINT uq_customer_orders_public_id UNIQUE (public_id),
    ADD CONSTRAINT chk_customer_orders_owner CHECK (num_nonnulls(user_id, guest_session_id) = 1),
    ADD CONSTRAINT chk_customer_orders_guest_access CHECK (
        (guest_session_id IS NULL AND guest_access_token_hash IS NULL)
        OR (guest_session_id IS NOT NULL AND guest_access_token_hash ~ '^[0-9a-f]{64}$')
    );

CREATE UNIQUE INDEX uq_customer_orders_guest_idempotency_key
    ON customer_orders(guest_session_id, idempotency_key)
    WHERE guest_session_id IS NOT NULL AND idempotency_key IS NOT NULL;
CREATE UNIQUE INDEX uq_customer_orders_one_pending_per_guest
    ON customer_orders(guest_session_id)
    WHERE guest_session_id IS NOT NULL AND status = 'PENDING_PAYMENT';
CREATE INDEX idx_customer_orders_guest_session ON customer_orders(guest_session_id);

ALTER TABLE shipping_quotes
    ADD COLUMN guest_session_id UUID REFERENCES guest_checkout_sessions(id) ON DELETE RESTRICT,
    ALTER COLUMN user_id DROP NOT NULL,
    ADD CONSTRAINT chk_shipping_quotes_owner CHECK (num_nonnulls(user_id, guest_session_id) = 1);
CREATE INDEX idx_shipping_quotes_guest_expiry ON shipping_quotes(guest_session_id, expires_at);

ALTER TABLE email_outbox
    ADD COLUMN order_public_id UUID,
    ADD COLUMN guest_order BOOLEAN;
UPDATE email_outbox entry
SET order_public_id = customer_order.public_id,
    guest_order = customer_order.guest_session_id IS NOT NULL
FROM customer_orders customer_order
WHERE customer_order.id = entry.order_id;
ALTER TABLE email_outbox
    ALTER COLUMN order_public_id SET NOT NULL,
    ALTER COLUMN guest_order SET NOT NULL;

CREATE FUNCTION populate_email_outbox_order_identity() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.order_public_id IS NULL OR NEW.guest_order IS NULL THEN
        SELECT customer_order.public_id, customer_order.guest_session_id IS NOT NULL
        INTO NEW.order_public_id, NEW.guest_order
        FROM customer_orders customer_order
        WHERE customer_order.id = NEW.order_id;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_email_outbox_populate_order_identity
    BEFORE INSERT ON email_outbox
    FOR EACH ROW EXECUTE FUNCTION populate_email_outbox_order_identity();

CREATE FUNCTION populate_customer_order_identity() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
    customer users%ROWTYPE;
BEGIN
    IF NEW.public_id IS NULL THEN
        NEW.public_id := gen_random_uuid();
    END IF;
    IF NEW.user_id IS NOT NULL AND (NEW.buyer_first_name IS NULL OR NEW.buyer_last_name IS NULL
            OR NEW.buyer_email IS NULL OR NEW.buyer_phone IS NULL OR NEW.buyer_document_number IS NULL) THEN
        SELECT * INTO customer FROM users WHERE id = NEW.user_id;
        NEW.buyer_first_name := COALESCE(NEW.buyer_first_name, customer.first_name);
        NEW.buyer_last_name := COALESCE(NEW.buyer_last_name, customer.last_name);
        NEW.buyer_email := COALESCE(NEW.buyer_email, lower(customer.email));
        NEW.buyer_phone := COALESCE(NEW.buyer_phone, NULLIF(trim(customer.phone), ''), 'N/A');
        NEW.buyer_document_number := COALESCE(NEW.buyer_document_number,
            NULLIF(trim(customer.document_number), ''), 'N/A');
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_customer_orders_populate_identity
    BEFORE INSERT ON customer_orders
    FOR EACH ROW EXECUTE FUNCTION populate_customer_order_identity();

CREATE FUNCTION prevent_customer_order_snapshot_update() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.public_id IS DISTINCT FROM OLD.public_id
       OR NEW.buyer_first_name IS DISTINCT FROM OLD.buyer_first_name
       OR NEW.buyer_last_name IS DISTINCT FROM OLD.buyer_last_name
       OR NEW.buyer_email IS DISTINCT FROM OLD.buyer_email
       OR NEW.buyer_phone IS DISTINCT FROM OLD.buyer_phone
       OR NEW.buyer_document_number IS DISTINCT FROM OLD.buyer_document_number THEN
        RAISE EXCEPTION 'customer order identity snapshots are immutable';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_customer_orders_immutable_snapshots
    BEFORE UPDATE ON customer_orders
    FOR EACH ROW EXECUTE FUNCTION prevent_customer_order_snapshot_update();
