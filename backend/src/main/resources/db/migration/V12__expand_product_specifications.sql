ALTER TABLE product_specifications
    ADD COLUMN group_name VARCHAR(100) NOT NULL DEFAULT 'General',
    ADD COLUMN is_highlighted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN display_order INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_product_specifications_display_order CHECK (display_order >= 0);

CREATE INDEX idx_product_specifications_product_order
    ON product_specifications(product_id, display_order, id);
