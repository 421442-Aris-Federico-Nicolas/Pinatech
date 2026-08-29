ALTER TABLE customer_orders
    ADD COLUMN payment_discount NUMERIC(19, 2) NOT NULL DEFAULT 0
        CHECK (payment_discount >= 0 AND payment_discount <= subtotal),
    ADD CONSTRAINT chk_customer_orders_payment_adjustments CHECK (
        (payment_method = 'MERCADO_PAGO' AND payment_discount = 0)
        OR (payment_method = 'BANK_TRANSFER' AND payment_surcharge = 0)
    ),
    ADD CONSTRAINT chk_customer_orders_amounts CHECK (
        total = subtotal + payment_surcharge - payment_discount
    );
