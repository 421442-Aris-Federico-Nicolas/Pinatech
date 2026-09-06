ALTER TABLE customer_orders
    ADD COLUMN cancellation_reason VARCHAR(40),
    ADD COLUMN cancellation_internal_detail VARCHAR(500),
    ADD COLUMN cancelled_by_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN cancelled_at TIMESTAMPTZ,
    ADD COLUMN refund_reference VARCHAR(200),
    ADD COLUMN refund_confirmed_by_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN refund_confirmed_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_customer_orders_cancellation_reason CHECK (
        cancellation_reason IS NULL OR cancellation_reason IN (
            'CUSTOMER_REQUEST', 'INVALID_DELIVERY_DATA', 'PRODUCT_UNAVAILABLE',
            'LOGISTICS_PROBLEM', 'DUPLICATE_OR_ERROR', 'OTHER'
        )
    );

ALTER TABLE order_shipments
    ADD COLUMN tracking_sync_pending BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN cancellation_scope VARCHAR(20),
    ADD COLUMN cancellation_reason VARCHAR(40),
    ADD COLUMN cancellation_internal_detail VARCHAR(500),
    ADD COLUMN cancellation_requested_by_user_id BIGINT REFERENCES users(id) ON DELETE RESTRICT,
    ADD COLUMN cancellation_requested_at TIMESTAMPTZ,
    ADD CONSTRAINT chk_order_shipments_cancellation_scope CHECK (
        cancellation_scope IS NULL OR cancellation_scope IN ('SHIPMENT_ONLY', 'ORDER')
    ),
    ADD CONSTRAINT chk_order_shipments_cancellation_reason CHECK (
        cancellation_reason IS NULL OR cancellation_reason IN (
            'CUSTOMER_REQUEST', 'INVALID_DELIVERY_DATA', 'PRODUCT_UNAVAILABLE',
            'LOGISTICS_PROBLEM', 'DUPLICATE_OR_ERROR', 'OTHER'
        )
    );

ALTER TABLE shipment_events ADD COLUMN provider_shipment_id BIGINT;

ALTER TABLE provider_payments DROP CONSTRAINT chk_provider_payments_refund_status;
ALTER TABLE provider_payments ADD CONSTRAINT chk_provider_payments_refund_status CHECK (
    refund_status IS NULL OR refund_status IN ('PENDING', 'APPROVED', 'REJECTED', 'AMOUNT_MISMATCH')
);

-- Legacy events may belong to a previous provider shipment after a replacement. They cannot be
-- backfilled safely; all events recorded after this migration carry the provider shipment snapshot.

INSERT INTO shipment_events (
    shipment_id, provider_shipment_id, event_key, raw_status, raw_substatus, occurred_at, recorded_at
)
SELECT shipments.id,
       shipments.provider_shipment_id,
       MD5('cancelled|' || shipments.provider_shipment_id::text) || MD5('zipnova|' || shipments.provider_shipment_id::text),
       COALESCE(shipments.raw_status, 'cancelled'),
       shipments.raw_substatus,
       shipments.updated_at,
       CURRENT_TIMESTAMP
FROM order_shipments shipments
WHERE shipments.status = 'CANCELLED'
  AND shipments.provider_shipment_id IS NOT NULL
   AND NOT EXISTS (
       SELECT 1 FROM shipment_events events
       WHERE events.shipment_id = shipments.id
         AND events.provider_shipment_id = shipments.provider_shipment_id
         AND LOWER(events.raw_status) IN ('canceled', 'cancelled')
   );

ALTER TABLE email_outbox ADD COLUMN deduplication_key VARCHAR(100) NOT NULL DEFAULT 'single';
ALTER TABLE email_outbox DROP CONSTRAINT uq_email_outbox_order_event;
ALTER TABLE email_outbox ADD CONSTRAINT uq_email_outbox_order_event_key
    UNIQUE (order_id, event_type, deduplication_key);
