ALTER TABLE product_images
    ADD CONSTRAINT uq_product_images_id_product UNIQUE (id, product_id);

ALTER TABLE product_variants
    ADD COLUMN image_id BIGINT,
    ADD CONSTRAINT fk_product_variants_image_product
        FOREIGN KEY (image_id, product_id)
        REFERENCES product_images(id, product_id)
        ON DELETE SET NULL (image_id);

CREATE INDEX idx_product_variants_image_id ON product_variants(image_id);
