package com.computerstore.home;

import com.computerstore.catalog.repository.CategoryRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.home.repository.HomeBannerRepository;
import com.computerstore.home.repository.HomeSectionReadRepository;
import com.computerstore.home.repository.HomeSectionRepository;
import com.computerstore.home.service.HomeSectionService;
import com.computerstore.home.service.HomeConfigurationLockService;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;
import com.computerstore.home.dto.HomeSectionOrderRequest;
import com.computerstore.home.dto.HomeSectionRequest;
import com.computerstore.storage.LocalImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
@AutoConfigureMockMvc
class HomeSectionReadIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HomeSectionReadRepository reads;
    @Autowired HomeSectionRepository sections;
    @Autowired HomeBannerRepository banners;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired HomeConfigurationLockService configurationLock;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;

    private HomeSectionService service;
    private LocalImageStorage storage;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM home_sections");
        storage = mock(LocalImageStorage.class);
        service = new HomeSectionService(sections, banners, reads, categories, products,
                storage, configurationLock);
    }

    @Test
    void filtersStockBeforeLimitSupportsManualFallbackAndHidesEmptySections() {
        Long peripherals = category("Peripherals");
        Long hardware = category("Hardware");
        Long brand = brand();
        Long unavailable = product("Unavailable", "30", peripherals, brand, true, 0);
        Long alpha = product("Alpha", "20", peripherals, brand, true, 4);
        Long beta = product("Beta", "10", hardware, brand, true, 2);

        Long manual = section(0, "Manual", "MANUAL", 2, null, true);
        configuredProduct(manual, unavailable, 0);
        configuredProduct(manual, alpha, 1);
        configuredProduct(manual, beta, 2);
        Long automatic = section(1, "Automatic", "AUTOMATIC", 2, "PRICE_ASC", true);
        configuredCategory(automatic, peripherals);
        configuredCategory(automatic, hardware);
        Long empty = section(2, "Empty", "AUTOMATIC", 12, "NAME_ASC", true);
        Long emptyCategory = category("Empty");
        configuredCategory(empty, emptyCategory);
        section(3, "Inactive", "AUTOMATIC", 12, "NAME_ASC", false);

        var response = service.publicSections();

        assertThat(response).extracting(item -> item.title()).containsExactly("Manual", "Automatic");
        assertThat(response.getFirst().products()).extracting(item -> item.id()).containsExactly(alpha, beta);
        assertThat(response.getFirst().categories()).isEmpty();
        assertThat(response.getLast().products()).extracting(item -> item.id()).containsExactly(beta, alpha);
        assertThat(response.getLast().categories()).extracting(item -> item.id())
                .containsExactlyInAnyOrder(peripherals, hardware);
    }

    @Test
    void automaticNewestUsesCreationTimeAcrossMultipleCategoriesAndReturnsCardImages() {
        Long firstCategory = category("First");
        Long secondCategory = category("Second");
        Long brand = brand();
        Long oldProduct = product("Old", "10", firstCategory, brand, true, 1);
        Long newProduct = product("New", "20", secondCategory, brand, true, 1);
        jdbc.update("UPDATE products SET created_at = CURRENT_TIMESTAMP - INTERVAL '2 days' WHERE id = ?", oldProduct);
        jdbc.update("UPDATE products SET created_at = CURRENT_TIMESTAMP WHERE id = ?", newProduct);
        Long imageId = jdbc.queryForObject("""
                INSERT INTO product_images (product_id, image_url, alt_text, display_order)
                VALUES (?, '/new.jpg', 'New product', 0) RETURNING id
                """, Long.class, newProduct);
        Long section = section(0, "Newest", "AUTOMATIC", 12, "NEWEST", true);
        configuredCategory(section, firstCategory);
        configuredCategory(section, secondCategory);

        var response = service.publicSections();

        assertThat(response).hasSize(1);
        assertThat(response.getFirst().products()).extracting(item -> item.id())
                .containsExactly(newProduct, oldProduct);
        assertThat(response.getFirst().products().getFirst().images().getFirst().id()).isEqualTo(imageId);
        assertThat(response.getFirst().products().getFirst().images().getFirst().contentUrl()).isEqualTo("/new.jpg");
    }

    @Test
    void putCanReverseManualProductsWithoutTransientConstraintViolations() throws Exception {
        Long category = category("Reorder");
        Long brand = brand();
        Long firstProduct = product("A", "10", category, brand, true, 1);
        Long secondProduct = product("B", "20", category, brand, true, 1);
        var section = service.create(new HomeSectionRequest(null, "Manual", null, null,
                HomeSectionMode.MANUAL, 2, null, List.of(),
                List.of(firstProduct, secondProduct), true));

        mvc.perform(put("/api/admin/home/sections/{id}", section.id())
                        .with(user("admin").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eyebrow":null,"title":"Manual","description":null,"buttonLabel":null,
                                 "mode":"MANUAL","productLimit":2,"sort":null,"categoryIds":[],
                                 "productIds":[%d,%d],"active":true}
                                """.formatted(secondProduct, firstProduct)))
                .andExpect(status().isOk());

        var updated = service.adminSections().getFirst();
        assertThat(updated.productIds()).containsExactly(secondProduct, firstProduct);
        assertThat(updated.products()).extracting(item -> item.id())
                .containsExactly(secondProduct, firstProduct);
    }

    @Test
    void adminCreateUpdateOrderAndHardDeletePersistNormalizedConfiguration() {
        Long firstCategory = category("First");
        Long secondCategory = category("Second");
        Long brand = brand();
        Long firstProduct = product("First", "10", firstCategory, brand, true, 1);
        Long secondProduct = product("Second", "20", secondCategory, brand, true, 1);
        Long imageId = jdbc.queryForObject("""
                INSERT INTO product_images (product_id, image_url, alt_text, display_order)
                VALUES (?, '/manual.jpg', 'Manual product', 0) RETURNING id
                """, Long.class, secondProduct);

        var first = service.create(new HomeSectionRequest(" Manual ", " First section ", null, null,
                HomeSectionMode.MANUAL, 2, HomeSectionSort.PRICE_DESC, List.of(firstCategory),
                List.of(secondProduct, firstProduct), true));
        var second = service.create(new HomeSectionRequest(null, "Second section", null, null,
                HomeSectionMode.AUTOMATIC, 1, HomeSectionSort.NAME_DESC,
                List.of(firstCategory, secondCategory), List.of(firstProduct), true));

        assertThat(first.sort()).isNull();
        assertThat(first.categoryIds()).isEmpty();
        assertThat(first.productIds()).containsExactly(secondProduct, firstProduct);
        assertThat(first.products()).extracting(item -> item.id()).containsExactly(secondProduct, firstProduct);
        assertThat(first.products().getFirst().images().getFirst().id()).isEqualTo(imageId);
        assertThat(second.productIds()).isEmpty();
        assertThat(second.products()).isEmpty();
        Long bannerId = jdbc.queryForObject("""
                INSERT INTO home_section_banners (
                    section_id, device, storage_key, original_filename, content_type, size_bytes)
                VALUES (?, 'DESKTOP', ?, 'banner.png', 'image/png', 10) RETURNING id
                """, Long.class, first.id(), UUID.randomUUID().toString());
        var withAdminBanner = service.adminSections().stream()
                .filter(item -> item.id().equals(first.id())).findFirst().orElseThrow();
        var withPublicBanner = service.publicSections().stream()
                .filter(item -> item.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(withAdminBanner.bannerDesktopUrl())
                .isEqualTo("/api/admin/home/banners/" + bannerId + "/content");
        assertThat(withPublicBanner.bannerDesktopUrl())
                .isEqualTo("/api/home/banners/" + bannerId + "/content?v=webp-1");
        jdbc.update("UPDATE products SET is_active = FALSE WHERE id = ?", secondProduct);
        jdbc.update("UPDATE inventory SET available_quantity = 0 WHERE variant_id IN "
                + "(SELECT id FROM product_variants WHERE product_id = ?)", firstProduct);
        var afterCatalogChanges = service.adminSections().stream()
                .filter(item -> item.id().equals(first.id())).findFirst().orElseThrow();
        assertThat(afterCatalogChanges.products()).extracting(item -> item.id())
                .containsExactly(secondProduct, firstProduct);
        assertThat(afterCatalogChanges.products()).extracting(item -> item.inStock())
                .containsExactly(false, false);
        service.reorder(new HomeSectionOrderRequest(List.of(second.id(), first.id())));
        assertThat(service.adminSections()).extracting(item -> item.id()).containsExactly(second.id(), first.id());

        var updated = service.update(first.id(), new HomeSectionRequest(null, "Updated", null, null,
                HomeSectionMode.AUTOMATIC, 12, HomeSectionSort.NEWEST,
                List.of(secondCategory), List.of(secondProduct), true));
        assertThat(updated.categoryIds()).containsExactly(secondCategory);
        assertThat(updated.productIds()).isEmpty();

        service.delete(first.id());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_sections WHERE id = ?", Integer.class, first.id()))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM home_section_categories WHERE section_id = ?",
                Integer.class, first.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id IN (?, ?)",
                Integer.class, firstProduct, secondProduct)).isEqualTo(2);
    }

    @Test
    void inactiveSectionBannerContentIsAdminOnly() {
        Long sectionId = section(0, "Inactive", "MANUAL", 1, null, false);
        Long category = category("Inactive banner");
        Long brand = brand();
        Long product = product("Configured", "10", category, brand, true, 1);
        configuredProduct(sectionId, product, 0);
        String storageKey = UUID.randomUUID().toString();
        Long bannerId = jdbc.queryForObject("""
                INSERT INTO home_section_banners (
                    section_id, device, storage_key, original_filename, content_type, size_bytes)
                VALUES (?, 'DESKTOP', ?, 'inactive.png', 'image/png', 10) RETURNING id
                """, Long.class, sectionId, storageKey);
        when(storage.publicWebp(storageKey)).thenReturn(
                new LocalImageStorage.StoredContent(java.nio.file.Path.of("inactive.webp"), 10));

        assertThrows(ResourceNotFoundException.class, () -> service.publicBannerContent(bannerId));
        assertThat(service.adminBannerContent(bannerId).fileName()).isEqualTo("inactive.webp");
    }

    @Test
    void advisoryLockSerializesConcurrentConfigurationTransactions() throws Exception {
        TransactionTemplate transactions = new TransactionTemplate(transactionManager);
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondStarted = new CountDownLatch(1);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> transactions.executeWithoutResult(status -> {
                configurationLock.lock();
                firstLocked.countDown();
                await(releaseFirst);
            }));
            assertThat(firstLocked.await(5, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> transactions.executeWithoutResult(status -> {
                secondStarted.countDown();
                configurationLock.lock();
            }));
            assertThat(secondStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Thread.sleep(200);
            assertThat(second.isDone()).isFalse();
            releaseFirst.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            releaseFirst.countDown();
        }
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }

    private Long category(String name) {
        String suffix = UUID.randomUUID().toString();
        return jdbc.queryForObject("INSERT INTO categories (name, slug) VALUES (?, ?) RETURNING id",
                Long.class, name + suffix, name.toLowerCase() + "-" + suffix);
    }

    private Long brand() {
        return jdbc.queryForObject("INSERT INTO brands (name) VALUES (?) RETURNING id",
                Long.class, "Brand-" + UUID.randomUUID());
    }

    private Long product(String name, String price, Long categoryId, Long brandId, boolean active, int stock) {
        String suffix = UUID.randomUUID().toString();
        Long productId = jdbc.queryForObject("""
                INSERT INTO products (name, slug, description, price, category_id, brand_id, is_active)
                VALUES (?, ?, 'Description', ?::numeric, ?, ?, ?) RETURNING id
                """, Long.class, name, name.toLowerCase() + "-" + suffix, price, categoryId, brandId, active);
        Long variantId = jdbc.queryForObject("""
                INSERT INTO product_variants (product_id, color_name, display_order)
                VALUES (?, 'Default', 0) RETURNING id
                """, Long.class, productId);
        jdbc.update("INSERT INTO inventory (variant_id, available_quantity, reserved_quantity) VALUES (?, ?, 0)",
                variantId, stock);
        return productId;
    }

    private Long section(int order, String title, String mode, int limit, String sort, boolean active) {
        return jdbc.queryForObject("""
                INSERT INTO home_sections (display_order, title, mode, product_limit, sort, is_active)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """, Long.class, order, title, mode, limit, sort, active);
    }

    private void configuredCategory(Long sectionId, Long categoryId) {
        jdbc.update("INSERT INTO home_section_categories (section_id, category_id) VALUES (?, ?)",
                sectionId, categoryId);
    }

    private void configuredProduct(Long sectionId, Long productId, int order) {
        jdbc.update("INSERT INTO home_section_products (section_id, product_id, display_order) VALUES (?, ?, ?)",
                sectionId, productId, order);
    }
}
