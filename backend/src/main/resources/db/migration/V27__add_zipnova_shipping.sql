ALTER TABLE user_addresses
    DROP CONSTRAINT chk_user_addresses_province_code,
    ALTER COLUMN province_code TYPE VARCHAR(4),
    ADD CONSTRAINT chk_user_addresses_province_code CHECK (province_code ~ '^[A-Z]{1,4}$');

ALTER TABLE customer_orders
    DROP CONSTRAINT chk_customer_orders_amounts,
    DROP CONSTRAINT chk_customer_orders_status,
    DROP CONSTRAINT chk_customer_orders_pickup_snapshot,
    ADD COLUMN shipping_cost NUMERIC(19, 2) NOT NULL DEFAULT 0 CHECK (shipping_cost >= 0),
    ADD COLUMN shipping_quote_id UUID,
    ADD COLUMN shipping_carrier_id BIGINT,
    ADD COLUMN shipping_carrier_name VARCHAR(150),
    ADD COLUMN shipping_service_code VARCHAR(100),
    ADD COLUMN shipping_service_name VARCHAR(150),
    ADD COLUMN shipping_logistic_type VARCHAR(100),
    ADD COLUMN shipping_eta TIMESTAMPTZ,
    ADD COLUMN delivery_recipient_name VARCHAR(200),
    ADD COLUMN delivery_document VARCHAR(50),
    ADD COLUMN delivery_email VARCHAR(254),
    ADD COLUMN delivery_phone VARCHAR(50),
    ADD COLUMN delivery_street VARCHAR(150),
    ADD COLUMN delivery_street_number VARCHAR(30),
    ADD COLUMN delivery_floor_apartment VARCHAR(50),
    ADD COLUMN delivery_locality VARCHAR(120),
    ADD COLUMN delivery_province VARCHAR(100),
    ADD COLUMN delivery_province_code VARCHAR(4),
    ADD COLUMN delivery_postal_code VARCHAR(12),
    ADD COLUMN delivery_country_code VARCHAR(2),
    ADD COLUMN delivery_reference VARCHAR(300),
    ADD CONSTRAINT chk_customer_orders_amounts CHECK (
        total = subtotal + shipping_cost + payment_surcharge - payment_discount
    ),
    ADD CONSTRAINT chk_customer_orders_status CHECK (status IN (
        'PENDING_PAYMENT', 'PAID', 'PREPARING', 'READY', 'SHIPPED', 'DELIVERED', 'CANCELLED'
    )),
    ADD CONSTRAINT chk_customer_orders_pickup_snapshot CHECK (
        (fulfillment_method IS NULL
            AND pickup_location_code IS NULL AND pickup_location_name IS NULL
            AND pickup_address_lines IS NULL AND pickup_locality IS NULL
            AND pickup_province_code IS NULL AND pickup_postal_code IS NULL
            AND pickup_instructions IS NULL AND pickup_hours IS NULL)
        OR (fulfillment_method = 'PICKUP'
            AND pickup_location_code IS NOT NULL AND pickup_location_name IS NOT NULL
            AND pickup_address_lines IS NOT NULL AND pickup_locality IS NOT NULL
            AND pickup_province_code IS NOT NULL AND pickup_postal_code IS NOT NULL
            AND pickup_instructions IS NOT NULL AND pickup_hours IS NOT NULL)
        OR (fulfillment_method = 'DELIVERY'
            AND pickup_location_code IS NULL AND pickup_location_name IS NULL
            AND pickup_address_lines IS NULL AND pickup_locality IS NULL
            AND pickup_province_code IS NULL AND pickup_postal_code IS NULL
            AND pickup_instructions IS NULL AND pickup_hours IS NULL)
    ),
    ADD CONSTRAINT chk_customer_orders_delivery_snapshot CHECK (
        (fulfillment_method IS DISTINCT FROM 'DELIVERY'
            AND shipping_cost = 0 AND shipping_quote_id IS NULL
            AND shipping_carrier_id IS NULL AND shipping_carrier_name IS NULL
            AND shipping_service_code IS NULL AND shipping_service_name IS NULL
            AND shipping_logistic_type IS NULL AND shipping_eta IS NULL
            AND delivery_recipient_name IS NULL AND delivery_document IS NULL
            AND delivery_email IS NULL AND delivery_phone IS NULL
            AND delivery_street IS NULL AND delivery_street_number IS NULL
            AND delivery_floor_apartment IS NULL AND delivery_locality IS NULL
            AND delivery_province IS NULL AND delivery_province_code IS NULL
            AND delivery_postal_code IS NULL AND delivery_country_code IS NULL
            AND delivery_reference IS NULL)
        OR (fulfillment_method = 'DELIVERY'
            AND delivery_method = 'ZIPNOVA' AND shipping_quote_id IS NOT NULL
            AND shipping_carrier_id IS NOT NULL AND shipping_carrier_name IS NOT NULL
            AND shipping_service_code IS NOT NULL AND shipping_service_name IS NOT NULL
            AND shipping_logistic_type IS NOT NULL
            AND delivery_recipient_name IS NOT NULL AND delivery_document IS NOT NULL
            AND delivery_email IS NOT NULL AND delivery_phone IS NOT NULL
            AND delivery_street IS NOT NULL AND delivery_street_number IS NOT NULL
            AND delivery_locality IS NOT NULL AND delivery_province IS NOT NULL
            AND delivery_province_code IS NOT NULL AND delivery_postal_code IS NOT NULL
            AND delivery_country_code = 'AR')
    );

ALTER TABLE order_items
    ADD COLUMN shipping_weight_grams INTEGER,
    ADD COLUMN shipping_height_cm INTEGER,
    ADD COLUMN shipping_width_cm INTEGER,
    ADD COLUMN shipping_length_cm INTEGER,
    ADD COLUMN shipping_classification_id VARCHAR(100),
    ADD COLUMN shipping_must_keep_vertical BOOLEAN,
    ADD CONSTRAINT chk_order_items_shipping_data CHECK (
        (shipping_weight_grams IS NULL AND shipping_height_cm IS NULL AND shipping_width_cm IS NULL
            AND shipping_length_cm IS NULL AND shipping_classification_id IS NULL
            AND shipping_must_keep_vertical IS NULL)
        OR (shipping_weight_grams BETWEEN 10 AND 10000000
            AND shipping_height_cm BETWEEN 1 AND 5000 AND shipping_width_cm BETWEEN 1 AND 5000
            AND shipping_length_cm BETWEEN 1 AND 5000 AND shipping_classification_id IS NOT NULL
            AND shipping_must_keep_vertical IS NOT NULL)
    );

CREATE TABLE shipping_quotes (
    id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    cart_hash VARCHAR(64) NOT NULL CHECK (cart_hash ~ '^[0-9a-f]{64}$'),
    profile_hash VARCHAR(64) NOT NULL CHECK (profile_hash ~ '^[0-9a-f]{64}$'),
    carrier_id BIGINT NOT NULL CHECK (carrier_id > 0),
    carrier_name VARCHAR(150) NOT NULL,
    service_code VARCHAR(100) NOT NULL CHECK (service_code <> 'pickup_point'),
    service_name VARCHAR(150) NOT NULL,
    logistic_type VARCHAR(100) NOT NULL,
    amount NUMERIC(19, 2) NOT NULL CHECK (amount >= 0),
    currency VARCHAR(3) NOT NULL CHECK (currency = 'ARS'),
    estimated_delivery_at TIMESTAMPTZ,
    tags JSONB NOT NULL DEFAULT '[]'::jsonb CHECK (jsonb_typeof(tags) = 'array'),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    consumed_order_id BIGINT UNIQUE REFERENCES customer_orders(id) ON DELETE RESTRICT
);
CREATE INDEX idx_shipping_quotes_user_expiry ON shipping_quotes(user_id, expires_at);
CREATE INDEX idx_shipping_quotes_cleanup ON shipping_quotes(expires_at) WHERE consumed_order_id IS NULL;

ALTER TABLE customer_orders
    ADD CONSTRAINT fk_customer_orders_shipping_quote
        FOREIGN KEY (shipping_quote_id) REFERENCES shipping_quotes(id) ON DELETE RESTRICT,
    ADD CONSTRAINT uq_customer_orders_shipping_quote UNIQUE (shipping_quote_id);

CREATE TABLE order_shipments (
    id UUID PRIMARY KEY,
    order_id BIGINT NOT NULL UNIQUE REFERENCES customer_orders(id) ON DELETE RESTRICT,
    status VARCHAR(30) NOT NULL,
    external_id VARCHAR(30) NOT NULL UNIQUE CHECK (external_id ~ '^[A-Za-z0-9-]+$'),
    provider_shipment_id BIGINT UNIQUE,
    raw_status VARCHAR(100),
    raw_substatus VARCHAR(100),
    carrier_tracking_id VARCHAR(200),
    tracking_url VARCHAR(2048),
    incident BOOLEAN NOT NULL DEFAULT FALSE,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    last_error VARCHAR(500),
    provider_updated_at TIMESTAMPTZ,
    estimated_delivery_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT chk_order_shipments_status CHECK (status IN (
        'PENDING_CREATE', 'CREATING', 'ACTIVE', 'RETRY', 'BLOCKED_PAYMENT', 'CANCELLED', 'DELIVERED', 'INCIDENT', 'FAILED'
    )),
    CONSTRAINT chk_order_shipments_provider_id CHECK (
        (status IN ('PENDING_CREATE', 'CREATING', 'RETRY', 'BLOCKED_PAYMENT', 'FAILED') AND provider_shipment_id IS NULL)
        OR (status IN ('ACTIVE', 'CANCELLED', 'DELIVERED', 'INCIDENT') AND provider_shipment_id IS NOT NULL)
    )
);
CREATE INDEX idx_order_shipments_work ON order_shipments(next_attempt_at, created_at)
    WHERE status IN ('PENDING_CREATE', 'CREATING', 'RETRY', 'ACTIVE', 'INCIDENT');

CREATE TABLE shipment_events (
    id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    shipment_id UUID NOT NULL REFERENCES order_shipments(id) ON DELETE RESTRICT,
    event_key VARCHAR(64) NOT NULL UNIQUE CHECK (event_key ~ '^[0-9a-f]{64}$'),
    raw_status VARCHAR(100) NOT NULL,
    raw_substatus VARCHAR(100),
    occurred_at TIMESTAMPTZ NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_shipment_events_shipment_time ON shipment_events(shipment_id, occurred_at, id);

CREATE TABLE shipping_webhook_inbox (
    id UUID PRIMARY KEY,
    provider_shipment_id BIGINT NOT NULL CHECK (provider_shipment_id > 0),
    payload_hash VARCHAR(64) NOT NULL UNIQUE CHECK (payload_hash ~ '^[0-9a-f]{64}$'),
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    last_error VARCHAR(500),
    received_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    CONSTRAINT chk_shipping_webhook_inbox_status CHECK (status IN ('PENDING', 'PROCESSING', 'DONE', 'FAILED'))
);
CREATE INDEX idx_shipping_webhook_inbox_due ON shipping_webhook_inbox(next_attempt_at, received_at)
    WHERE status IN ('PENDING', 'PROCESSING');
CREATE INDEX idx_shipping_webhook_inbox_cleanup ON shipping_webhook_inbox(processed_at)
    WHERE status IN ('DONE', 'FAILED');

ALTER TABLE email_outbox DROP CONSTRAINT chk_email_outbox_seller_payload;
ALTER TABLE email_outbox ADD CONSTRAINT chk_email_outbox_seller_payload CHECK (
    (event_type IN ('SELLER_ORDER_CREATED', 'SELLER_PAYMENT_APPROVED', 'SHIPMENT_TRACKING_AVAILABLE')
        AND seller_payload IS NOT NULL)
    OR (event_type NOT IN ('SELLER_ORDER_CREATED', 'SELLER_PAYMENT_APPROVED', 'SHIPMENT_TRACKING_AVAILABLE')
        AND seller_payload IS NULL)
);
