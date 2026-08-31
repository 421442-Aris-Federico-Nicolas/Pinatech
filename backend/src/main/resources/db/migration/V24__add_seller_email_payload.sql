ALTER TABLE email_outbox
    ADD COLUMN seller_payload TEXT;

ALTER TABLE email_outbox
    ADD CONSTRAINT chk_email_outbox_seller_payload
    CHECK (
        (event_type IN ('SELLER_ORDER_CREATED', 'SELLER_PAYMENT_APPROVED') AND seller_payload IS NOT NULL)
        OR
        (event_type NOT IN ('SELLER_ORDER_CREATED', 'SELLER_PAYMENT_APPROVED') AND seller_payload IS NULL)
    );
