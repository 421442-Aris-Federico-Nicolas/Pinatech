package com.computerstore.catalog.repository;

import com.computerstore.catalog.domain.*;
import com.computerstore.catalog.controller.ProductController;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DataJpaTest(showSql = false, properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProductCardsRepositoryTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired EntityManager em;
    @Autowired ProductRepository products;

    @Test
    void legacyListAndDetailFetchCategoryAndBrandWithoutPerRowQueries() {
        for (int index = 0; index < 3; index++) {
            var category = new Category("Category " + index, "category-" + index);
            var brand = new Brand("Brand " + index);
            em.persist(category);
            em.persist(brand);
            product("Product " + index, "100", category, brand);
        }
        em.flush();
        em.clear();
        var controller = new ProductController(products, mock(ProductSpecificationRepository.class),
                mock(ProductVariantRepository.class), mock(InventoryRepository.class), mock(ProductImageService.class));
        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var page = controller.list(null, null, null, null, null, PageRequest.of(0, 2, Sort.by("name")));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(item -> item.categoryName()).containsExactly("Category 0", "Category 1");
        assertThat(page.getContent()).extracting(item -> item.brandName()).containsExactly("Brand 0", "Brand 1");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        em.clear();
        statistics.clear();
        var detail = controller.detail(page.getContent().getFirst().id());
        assertThat(detail.description()).isEqualTo("Detailed description");
        assertThat(detail.categoryName()).isEqualTo("Category 0");
        assertThat(detail.brandName()).isEqualTo("Brand 0");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void projectsFirstImageAndActiveStockWithConstantSqlBudgetAndFilters() {
        var category = new Category("Hardware", "hardware");
        var brand = new Brand("Brand");
        em.persist(category);
        em.persist(brand);
        var first = product("Alpha", "100", category, brand);
        var second = product("Beta", "200", category, brand);
        var third = product("Gamma", "300", category, brand);
        product("Hidden", "100", category, brand).deactivate();
        var later = new ProductImage(first, "Later", 2, null, null, null, 0);
        em.persist(later);
        var image = new ProductImage(first, "Main", 0, "stored-key", "main.png", "image/png", 10);
        em.persist(image);
        em.persist(new ProductImage(first, "Tie", 0, null, null, null, 0));
        var external = new ProductImage(second, "External", 0, null, null, null, 0);
        external.setContentUrl("https://example.com/image.png");
        em.persist(external);
        stock(first, true, 5);
        stock(first, true, 2);
        stock(second, false, 10);
        stock(second, true, 0);
        em.persist(new ProductVariant(third, "No inventory", null, null, 0));
        em.flush();
        em.clear();

        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var page = products.findCards(null, null, java.util.List.of(-1L), false, null, null, null,
                PageRequest.of(0, 2, Sort.by("name")));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).extracting(item -> item.name()).containsExactly("Alpha", "Beta");
        assertThat(page.getContent().getFirst().images()).hasSize(1);
        assertThat(page.getContent().getFirst().images().getFirst().id()).isEqualTo(image.getId());
        assertThat(page.getContent().getFirst().images().getFirst().contentUrl())
                .isEqualTo("/api/products/images/" + image.getId() + "/thumbnail");
        assertThat(page.getContent().getFirst().inStock()).isTrue();
        assertThat(page.getContent().getLast().inStock()).isFalse();
        assertThat(page.getContent().getLast().images().getFirst().contentUrl()).isEqualTo("https://example.com/image.png");
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();

        statistics.clear();
        var all = products.findCards(null, null, java.util.List.of(-1L), false, null, null, null,
                PageRequest.of(0, 3, Sort.by("price").descending()));
        assertThat(all.getContent().getFirst().images()).isEmpty();
        assertThat(all.getContent().getFirst().inStock()).isFalse();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isZero();

        var filtered = products.findCards("%alp%", category.getId(), java.util.List.of(-1L), false, brand.getId(), new BigDecimal("100"),
                new BigDecimal("100"), PageRequest.of(0, 1));
        assertThat(filtered.getTotalElements()).isEqualTo(1);
        assertThat(filtered.getContent().getFirst().id()).isEqualTo(first.getId());
        assertThat(products.findCards(null, -1L, java.util.List.of(-1L), false, null, null, null, PageRequest.of(0, 12))).isEmpty();
        assertThat(products.findCards(null, null, java.util.List.of(-1L), false, -1L, null, null, PageRequest.of(0, 12))).isEmpty();
        assertThat(products.findCards(null, null, java.util.List.of(-1L), false, null, new BigDecimal("301"), null, PageRequest.of(0, 12))).isEmpty();
        assertThat(products.findCards(null, null, java.util.List.of(-1L), false, null, null, new BigDecimal("99"), PageRequest.of(0, 12))).isEmpty();
        assertThat(products.findCards("%missing%", null, java.util.List.of(-1L), false, null, null, null, PageRequest.of(0, 12))).isEmpty();
        assertThat(products.findCards(null, null, java.util.List.of(-1L), false, null, null, null, PageRequest.of(2, 2)).getTotalElements()).isEqualTo(3);
    }

    @Test
    void filtersCardsByAnyCategoryIdAndKeepsSingularCompatibility() {
        var firstCategory = new Category("First", "first");
        var secondCategory = new Category("Second", "second");
        var excludedCategory = new Category("Excluded", "excluded");
        var brand = new Brand("Brand");
        em.persist(firstCategory);
        em.persist(secondCategory);
        em.persist(excludedCategory);
        em.persist(brand);
        var first = product("Alpha", "100", firstCategory, brand);
        var second = product("Beta", "200", secondCategory, brand);
        product("Gamma", "300", excludedCategory, brand);
        em.flush();
        em.clear();

        var statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        var multi = products.findCards(null, null, java.util.List.of(firstCategory.getId(), secondCategory.getId()),
                true, null, null, null, PageRequest.of(0, 1, Sort.by("name")));
        assertThat(multi.getTotalElements()).isEqualTo(2);
        assertThat(multi.getContent()).extracting(item -> item.id()).containsExactly(first.getId());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);

        var singular = products.findCards(null, secondCategory.getId(), java.util.List.of(-1L), false,
                null, null, null, PageRequest.of(0, 12, Sort.by("name")));
        assertThat(singular.getTotalElements()).isEqualTo(1);
        assertThat(singular.getContent()).extracting(item -> item.id()).containsExactly(second.getId());
    }

    private Product product(String name, String price, Category category, Brand brand) {
        var product = new Product(name, name.toLowerCase(), "Detailed description", new BigDecimal(price),
                category, brand, null, null, null, null, null, false);
        em.persist(product);
        return product;
    }

    private void stock(Product product, boolean active, int quantity) {
        var variant = new ProductVariant(product, "Color " + quantity, null, null, 0);
        if (!active) variant.deactivate();
        em.persist(variant);
        var inventory = new Inventory(variant);
        inventory.adjust(quantity);
        em.persist(inventory);
    }
}
