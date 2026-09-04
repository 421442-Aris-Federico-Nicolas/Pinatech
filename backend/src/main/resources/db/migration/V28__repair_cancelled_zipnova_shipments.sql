UPDATE order_shipments
SET status = 'CANCELLED',
    incident = TRUE,
    last_error = COALESCE(last_error, 'Provider shipment cancelled; the order requires operational resolution.'),
    lease_until = NULL,
    lease_token = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE provider_shipment_id IS NOT NULL
  AND LOWER(COALESCE(raw_status, '')) IN ('canceled', 'cancelled')
  AND status <> 'DELIVERED';

UPDATE customer_orders orders
SET fulfillment_status = 'CANCELLED',
    updated_at = CURRENT_TIMESTAMP
FROM order_shipments shipments
WHERE shipments.order_id = orders.id
  AND shipments.status = 'CANCELLED'
  AND orders.status NOT IN ('CANCELLED', 'DELIVERED');
