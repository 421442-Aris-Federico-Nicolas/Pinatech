ALTER TABLE product_images
    DROP CONSTRAINT chk_product_images_storage_metadata,
    ADD CONSTRAINT chk_product_images_storage_metadata CHECK (
        (storage_key IS NULL AND original_filename IS NULL AND content_type IS NULL AND size_bytes IS NULL AND created_at IS NULL)
        OR
        (storage_key IS NOT NULL AND original_filename IS NOT NULL
            AND content_type IN ('image/jpeg', 'image/png', 'image/webp')
            AND size_bytes > 0 AND created_at IS NOT NULL)
    );

ALTER TABLE home_section_banners
    DROP CONSTRAINT chk_home_section_banners_source,
    ADD CONSTRAINT chk_home_section_banners_source CHECK (
        (external_url IS NOT NULL AND BTRIM(external_url) <> '' AND storage_key IS NULL
            AND original_filename IS NULL AND content_type IS NULL AND size_bytes IS NULL)
        OR
        (external_url IS NULL AND storage_key IS NOT NULL AND original_filename IS NOT NULL
            AND content_type IS NOT NULL AND content_type IN ('image/jpeg', 'image/png', 'image/webp')
            AND size_bytes IS NOT NULL AND size_bytes > 0)
    );

ALTER TABLE home_hero_images
    DROP CONSTRAINT home_hero_images_content_type_check,
    ADD CONSTRAINT home_hero_images_content_type_check
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp'));
