ALTER TABLE customer_orders
    ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'ARS',
    ADD COLUMN payment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN fulfillment_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN payment_method VARCHAR(50),
    ADD COLUMN delivery_method VARCHAR(50);

UPDATE customer_orders
SET payment_status = CASE status
        WHEN 'PENDING_PAYMENT' THEN 'PENDING'
        WHEN 'CANCELLED' THEN 'CANCELLED'
        ELSE 'APPROVED'
    END,
    fulfillment_status = CASE status
        WHEN 'PREPARING' THEN 'PREPARING'
        WHEN 'READY' THEN 'READY'
        WHEN 'DELIVERED' THEN 'DELIVERED'
        WHEN 'CANCELLED' THEN 'CANCELLED'
        ELSE 'PENDING'
    END;

ALTER TABLE customer_orders
    ADD CONSTRAINT chk_customer_orders_payment_status CHECK (payment_status IN (
        'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'REFUNDED'
    )),
    ADD CONSTRAINT chk_customer_orders_fulfillment_status CHECK (fulfillment_status IN (
        'PENDING', 'PREPARING', 'READY', 'SHIPPED', 'DELIVERED', 'CANCELLED'
    ));
