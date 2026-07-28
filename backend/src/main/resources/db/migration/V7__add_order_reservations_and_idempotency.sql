ALTER TABLE customer_orders
    ADD COLUMN reservation_expires_at TIMESTAMPTZ,
    ADD COLUMN idempotency_key VARCHAR(100),
    ADD COLUMN request_hash VARCHAR(64);

UPDATE customer_orders
SET reservation_expires_at = created_at + INTERVAL '15 minutes';

ALTER TABLE customer_orders
    ALTER COLUMN reservation_expires_at SET NOT NULL,
    ADD CONSTRAINT chk_customer_orders_idempotency_hash
        CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));

CREATE UNIQUE INDEX uq_customer_orders_user_idempotency_key
    ON customer_orders(user_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE INDEX idx_customer_orders_expiring_reservations
    ON customer_orders(reservation_expires_at, id)
    WHERE status = 'PENDING_PAYMENT';

ALTER TABLE inventory_movements
    ALTER COLUMN created_by_user_id DROP NOT NULL,
    DROP CONSTRAINT chk_inventory_movements_type,
    ADD CONSTRAINT chk_inventory_movements_type CHECK (movement_type IN (
        'INITIAL_STOCK', 'PURCHASE', 'SALE', 'RESERVATION', 'CANCELLATION', 'ADJUSTMENT',
        'RETURN', 'RELEASE', 'CONSUMPTION'
    ));

INSERT INTO inventory_movements (
    product_id, movement_type, quantity, reason, order_id, created_by_user_id, created_at
)
SELECT
    item.product_id,
    'CONSUMPTION',
    item.quantity,
    'Reservation reconciled during order inventory migration',
    customer_order.id,
    NULL,
    customer_order.updated_at
FROM order_items item
JOIN customer_orders customer_order ON customer_order.id = item.order_id
WHERE customer_order.status IN ('PREPARING', 'READY', 'DELIVERED');

WITH consumed AS (
    SELECT item.product_id, SUM(item.quantity)::INTEGER AS quantity
    FROM order_items item
    JOIN customer_orders customer_order ON customer_order.id = item.order_id
    WHERE customer_order.status IN ('PREPARING', 'READY', 'DELIVERED')
    GROUP BY item.product_id
)
UPDATE inventory
SET reserved_quantity = inventory.reserved_quantity - consumed.quantity,
    updated_at = CURRENT_TIMESTAMP
FROM consumed
WHERE inventory.product_id = consumed.product_id;
