package com.computerstore.home.repository;

import com.computerstore.catalog.dto.ProductListItemResponse;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;
import com.computerstore.home.dto.HomeCategoryResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class HomeSectionReadRepository {
    private final JdbcTemplate jdbc;

    public HomeSectionReadRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<SectionRow> activeSections() {
        return jdbc.query("""
                SELECT section.id, section.display_order, section.eyebrow, section.title,
                       section.description, section.button_label, section.mode, section.product_limit,
                       section.sort,
                       CASE WHEN desktop.storage_key IS NOT NULL
                             THEN '/api/home/banners/' || desktop.id || '/content?v=webp-1'
                            ELSE desktop.external_url END AS desktop_url,
                       CASE WHEN mobile.storage_key IS NOT NULL
                             THEN '/api/home/banners/' || mobile.id || '/content?v=webp-1'
                            ELSE mobile.external_url END AS mobile_url
                FROM home_sections section
                LEFT JOIN home_section_banners desktop
                  ON desktop.section_id = section.id AND desktop.device = 'DESKTOP'
                LEFT JOIN home_section_banners mobile
                  ON mobile.section_id = section.id AND mobile.device = 'MOBILE'
                WHERE section.is_active
                ORDER BY section.display_order, section.id
                """, (rs, rowNum) -> new SectionRow(
                rs.getLong("id"), rs.getInt("display_order"), rs.getString("eyebrow"),
                rs.getString("title"), rs.getString("description"), rs.getString("button_label"),
                HomeSectionMode.valueOf(rs.getString("mode")), rs.getInt("product_limit"),
                rs.getString("sort") == null ? null : HomeSectionSort.valueOf(rs.getString("sort")),
                rs.getString("desktop_url"), rs.getString("mobile_url")));
    }

    public List<AdminSectionRow> allSections() {
        return jdbc.query("""
                SELECT section.id, section.display_order, section.eyebrow, section.title,
                       section.description, section.button_label, section.mode, section.product_limit,
                       section.sort, section.is_active,
                       CASE WHEN desktop.storage_key IS NOT NULL
                            THEN '/api/admin/home/banners/' || desktop.id || '/content'
                            ELSE desktop.external_url END AS desktop_url,
                       CASE WHEN mobile.storage_key IS NOT NULL
                            THEN '/api/admin/home/banners/' || mobile.id || '/content'
                            ELSE mobile.external_url END AS mobile_url
                FROM home_sections section
                LEFT JOIN home_section_banners desktop
                  ON desktop.section_id = section.id AND desktop.device = 'DESKTOP'
                LEFT JOIN home_section_banners mobile
                  ON mobile.section_id = section.id AND mobile.device = 'MOBILE'
                ORDER BY section.display_order, section.id
                """, (rs, rowNum) -> new AdminSectionRow(
                rs.getLong("id"), rs.getInt("display_order"), rs.getString("eyebrow"),
                rs.getString("title"), rs.getString("description"), rs.getString("button_label"),
                HomeSectionMode.valueOf(rs.getString("mode")), rs.getInt("product_limit"),
                rs.getString("sort") == null ? null : HomeSectionSort.valueOf(rs.getString("sort")),
                rs.getBoolean("is_active"), rs.getString("desktop_url"), rs.getString("mobile_url")));
    }

    public List<SectionCategoryIdRow> allSectionCategoryIds() {
        return jdbc.query("""
                SELECT section_id, category_id
                FROM home_section_categories
                ORDER BY section_id, category_id
                """, (rs, rowNum) -> new SectionCategoryIdRow(
                rs.getLong("section_id"), rs.getLong("category_id")));
    }

    public List<ProductRow> allConfiguredProducts() {
        return jdbc.query("""
                SELECT configured.section_id, product.id, product.name, product.slug, product.price,
                       category.id AS category_id, category.name AS category_name,
                       brand.id AS brand_id, brand.name AS brand_name,
                       image.id AS image_id, image.image_url, image.storage_key, image.alt_text,
                       image.original_filename, image.display_order AS image_display_order,
                       product.is_active AND category.is_active AND brand.is_active AND EXISTS (
                           SELECT 1 FROM product_variants variant
                           JOIN inventory stock ON stock.variant_id = variant.id
                           WHERE variant.product_id = product.id AND variant.is_active
                             AND stock.available_quantity > 0) AS in_stock
                FROM home_section_products configured
                JOIN home_sections section ON section.id = configured.section_id AND section.mode = 'MANUAL'
                JOIN products product ON product.id = configured.product_id
                JOIN categories category ON category.id = product.category_id
                JOIN brands brand ON brand.id = product.brand_id
                LEFT JOIN LATERAL (
                    SELECT candidate.id, candidate.image_url, candidate.storage_key, candidate.alt_text,
                           candidate.original_filename, candidate.display_order
                    FROM product_images candidate
                    WHERE candidate.product_id = product.id
                    ORDER BY candidate.display_order, candidate.id
                    LIMIT 1
                ) image ON TRUE
                ORDER BY configured.section_id, configured.display_order
                """, (rs, rowNum) -> new ProductRow(rs.getLong("section_id"), product(rs, rs.getBoolean("in_stock"))));
    }

    public List<CategoryRow> activeSectionCategories() {
        return jdbc.query("""
                SELECT relation.section_id, category.id, category.name, category.slug
                FROM home_section_categories relation
                JOIN home_sections section ON section.id = relation.section_id AND section.is_active
                JOIN categories category ON category.id = relation.category_id AND category.is_active
                ORDER BY relation.section_id, category.name, category.id
                """, (rs, rowNum) -> new CategoryRow(rs.getLong("section_id"),
                new HomeCategoryResponse(rs.getLong("id"), rs.getString("name"), rs.getString("slug"))));
    }

    public List<ProductRow> eligibleProducts() {
        return jdbc.query("""
                WITH eligible AS (
                    SELECT section.id AS section_id, product.id, product.name, product.slug, product.price,
                           category.id AS category_id, category.name AS category_name,
                           brand.id AS brand_id, brand.name AS brand_name,
                           image.id AS image_id, image.image_url, image.storage_key, image.alt_text,
                           image.original_filename, image.display_order AS image_display_order,
                           ROW_NUMBER() OVER (PARTITION BY section.id ORDER BY
                               CASE WHEN section.mode = 'MANUAL' THEN configured.display_order END ASC NULLS LAST,
                               CASE WHEN section.mode = 'AUTOMATIC' AND section.sort = 'NAME_ASC' THEN LOWER(product.name) END ASC NULLS LAST,
                               CASE WHEN section.mode = 'AUTOMATIC' AND section.sort = 'NAME_DESC' THEN LOWER(product.name) END DESC NULLS LAST,
                               CASE WHEN section.mode = 'AUTOMATIC' AND section.sort = 'PRICE_ASC' THEN product.price END ASC NULLS LAST,
                               CASE WHEN section.mode = 'AUTOMATIC' AND section.sort = 'PRICE_DESC' THEN product.price END DESC NULLS LAST,
                               CASE WHEN section.mode = 'AUTOMATIC' AND section.sort = 'NEWEST' THEN product.created_at END DESC NULLS LAST,
                               product.id ASC) AS product_rank,
                           section.product_limit
                    FROM home_sections section
                    LEFT JOIN home_section_products configured ON configured.section_id = section.id
                    JOIN products product ON
                        (section.mode = 'MANUAL' AND product.id = configured.product_id)
                        OR (section.mode = 'AUTOMATIC' AND EXISTS (
                            SELECT 1 FROM home_section_categories selected
                            WHERE selected.section_id = section.id AND selected.category_id = product.category_id))
                    JOIN categories category ON category.id = product.category_id AND category.is_active
                    JOIN brands brand ON brand.id = product.brand_id AND brand.is_active
                    LEFT JOIN LATERAL (
                        SELECT candidate.id, candidate.image_url, candidate.storage_key, candidate.alt_text,
                               candidate.original_filename, candidate.display_order
                        FROM product_images candidate
                        WHERE candidate.product_id = product.id
                        ORDER BY candidate.display_order, candidate.id
                        LIMIT 1
                    ) image ON TRUE
                    WHERE section.is_active AND product.is_active
                      AND EXISTS (
                          SELECT 1 FROM product_variants variant
                          JOIN inventory stock ON stock.variant_id = variant.id
                          WHERE variant.product_id = product.id AND variant.is_active
                            AND stock.available_quantity > 0)
                )
                SELECT * FROM eligible WHERE product_rank <= product_limit
                ORDER BY section_id, product_rank
                """, (rs, rowNum) -> new ProductRow(rs.getLong("section_id"), product(rs, true)));
    }

    private ProductListItemResponse product(java.sql.ResultSet rs, boolean inStock) throws java.sql.SQLException {
        return new ProductListItemResponse(rs.getLong("id"), rs.getString("name"), rs.getString("slug"),
                rs.getBigDecimal("price"), rs.getLong("category_id"), rs.getString("category_name"),
                rs.getLong("brand_id"), rs.getString("brand_name"),
                nullableLong(rs, "image_id"), rs.getString("image_url"), rs.getString("storage_key"),
                rs.getString("alt_text"), rs.getString("original_filename"),
                nullableInteger(rs, "image_display_order"), inStock);
    }

    private Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record SectionRow(Long id, int displayOrder, String eyebrow, String title, String description,
            String buttonLabel, HomeSectionMode mode, int productLimit, HomeSectionSort sort,
            String bannerDesktopUrl, String bannerMobileUrl) {}
    public record AdminSectionRow(Long id, int displayOrder, String eyebrow, String title, String description,
            String buttonLabel, HomeSectionMode mode, int productLimit, HomeSectionSort sort, boolean active,
            String bannerDesktopUrl, String bannerMobileUrl) {}
    public record SectionCategoryIdRow(Long sectionId, Long categoryId) {}
    public record CategoryRow(Long sectionId, HomeCategoryResponse category) {}
    public record ProductRow(Long sectionId, ProductListItemResponse product) {}
}
