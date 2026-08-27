ALTER TABLE customer_orders
    ADD COLUMN fulfillment_method VARCHAR(30),
    ADD COLUMN pickup_location_code VARCHAR(100),
    ADD COLUMN pickup_location_name VARCHAR(150),
    ADD COLUMN pickup_address_lines VARCHAR(1000),
    ADD COLUMN pickup_locality VARCHAR(100),
    ADD COLUMN pickup_province_code VARCHAR(20),
    ADD COLUMN pickup_postal_code VARCHAR(20),
    ADD COLUMN pickup_instructions VARCHAR(1000),
    ADD COLUMN pickup_hours VARCHAR(500),
    ADD CONSTRAINT chk_customer_orders_fulfillment_method CHECK (
        fulfillment_method IS NULL OR fulfillment_method IN ('PICKUP', 'DELIVERY')
    ),
    ADD CONSTRAINT chk_customer_orders_pickup_snapshot CHECK (
        (fulfillment_method IS NULL
            AND pickup_location_code IS NULL
            AND pickup_location_name IS NULL
            AND pickup_address_lines IS NULL
            AND pickup_locality IS NULL
            AND pickup_province_code IS NULL
            AND pickup_postal_code IS NULL
            AND pickup_instructions IS NULL
            AND pickup_hours IS NULL)
        OR
        (fulfillment_method = 'PICKUP'
            AND pickup_location_code IS NOT NULL
            AND pickup_location_name IS NOT NULL
            AND pickup_address_lines IS NOT NULL
            AND pickup_locality IS NOT NULL
            AND pickup_province_code IS NOT NULL
            AND pickup_postal_code IS NOT NULL
            AND pickup_instructions IS NOT NULL
            AND pickup_hours IS NOT NULL)
        OR
        (fulfillment_method = 'DELIVERY'
            AND pickup_location_code IS NULL
            AND pickup_location_name IS NULL
            AND pickup_address_lines IS NULL
            AND pickup_locality IS NULL
            AND pickup_province_code IS NULL
            AND pickup_postal_code IS NULL
            AND pickup_instructions IS NULL
            AND pickup_hours IS NULL)
    );

CREATE INDEX idx_customer_orders_fulfillment_method_location
    ON customer_orders(fulfillment_method, pickup_location_code);
