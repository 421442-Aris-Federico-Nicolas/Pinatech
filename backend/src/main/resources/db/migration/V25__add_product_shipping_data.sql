ALTER TABLE products
    ADD COLUMN shipping_weight_grams INTEGER,
    ADD COLUMN shipping_height_cm INTEGER,
    ADD COLUMN shipping_width_cm INTEGER,
    ADD COLUMN shipping_length_cm INTEGER,
    ADD COLUMN shipping_classification_id INTEGER,
    ADD COLUMN must_keep_vertical BOOLEAN NOT NULL DEFAULT FALSE,
    ADD CONSTRAINT chk_products_shipping_data CHECK (
        (
            shipping_weight_grams IS NULL
            AND shipping_height_cm IS NULL
            AND shipping_width_cm IS NULL
            AND shipping_length_cm IS NULL
            AND shipping_classification_id IS NULL
        )
        OR (
            shipping_weight_grams IS NOT NULL
            AND shipping_weight_grams BETWEEN 10 AND 10000000
            AND shipping_height_cm IS NOT NULL
            AND shipping_height_cm BETWEEN 1 AND 5000
            AND shipping_width_cm IS NOT NULL
            AND shipping_width_cm BETWEEN 1 AND 5000
            AND shipping_length_cm IS NOT NULL
            AND shipping_length_cm BETWEEN 1 AND 5000
            AND shipping_classification_id IS NOT NULL
            AND shipping_classification_id BETWEEN 1 AND 8
        )
    );
