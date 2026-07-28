INSERT INTO inventory (product_id, available_quantity, reserved_quantity)
SELECT id, 10, 0 FROM products
ON CONFLICT (product_id) DO NOTHING;
