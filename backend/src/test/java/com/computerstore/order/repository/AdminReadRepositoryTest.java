package com.computerstore.order.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.dto.InventoryPageResponse;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.controller.AdminOrderController;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderItem;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.dto.OrderResponseMapper;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.domain.BankTransferProof;
import com.computerstore.payment.domain.BankTransferProofStatus;
import com.computerstore.payment.dto.AdminBankTransferProofResponse;
import com.computerstore.payment.repository.BankTransferProofRepository;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.EntityManager;
import org.hibernate.Hibernate;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest(properties = {"spring.jpa.properties.hibernate.generate_statistics=true",
        "logging.level.org.hibernate.engine.internal.StatisticalLoggingSessionEventListener=OFF",
        "spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch=true"}, showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class AdminReadRepositoryTest {
    @Autowired EntityManager em;
    @Autowired CustomerOrderRepository orders;
    @Autowired InventoryRepository inventory;
    @Autowired BankTransferProofRepository proofs;
    private Statistics statistics;
    private final List<Long> orderIds = new ArrayList<>();

    @BeforeEach
    void fixture() {
        for (int i = 0; i < 7; i++) {
            var category = new Category("Read category " + i, "read-category-" + i);
            var brand = new Brand("Read brand " + i);
            em.persist(category);
            em.persist(brand);
            var product = new Product("Read product " + i, "read-product-" + i, "Description",
                    new BigDecimal("10"), category, brand, null, null, null, null, null, false);
            em.persist(product);
            var variant = new ProductVariant(product, "Blue", "#0000FF", null, 0);
            em.persist(variant);
            var stock = new Inventory(variant);
            stock.adjust(i);
            em.persist(stock);
            var user = new UserAccount("Read", "User " + i, "read" + i + "@example.com", "hash", null);
            em.persist(user);
            var order = new CustomerOrder(user, List.of(new OrderItem(variant, 1), new OrderItem(variant, 1)),
                    new BigDecimal("20"), Instant.now().plusSeconds(3600), null, null);
            if (i < 2) order.approveMercadoPagoPayment();
            em.persist(order);
            orderIds.add(order.getId());
            var proof = new BankTransferProof(order, "raw-" + i, "proof.png", "image/png", 10,
                    "a".repeat(64), null, Instant.now());
            proof.addPreview("preview-" + i + "-0", 0, 10, 10, 10, "b".repeat(64));
            proof.addPreview("preview-" + i + "-1", 1, 10, 10, 10, "c".repeat(64));
            em.persist(proof);
        }
        em.flush();
        em.clear();
        statistics = em.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void orderDetailsAndLegacyListFetchOneBagWithoutRolesOrNPlusOne() {
        var loaded = orders.findDetailsByIds(orderIds);
        assertThat(loaded).hasSize(7);
        assertThat(loaded.stream().map(OrderResponseMapper::toResponse).toList())
                .allSatisfy(order -> assertThat(order.items()).hasSize(2));
        assertThat(loaded).allSatisfy(order -> assertThat(Hibernate.isInitialized(
                org.springframework.test.util.ReflectionTestUtils.getField(order.getUser(), "roles"))).isFalse());
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        em.clear();
        statistics.clear();
        assertThat(orders.findAllByOrderByCreatedAtDesc().stream().map(OrderResponseMapper::toResponse).toList())
                .hasSize(7);
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void pageUsesDatabaseLimitsAndAccurateFilteredCount() {
        var controller = new AdminOrderController(orders, mock(OrderStockService.class));
        var page = controller.page(1, 2, OrderStatus.PENDING_PAYMENT);
        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getContent()).hasSize(2).allSatisfy(order -> {
            assertThat(order.status()).isEqualTo("PENDING_PAYMENT");
            assertThat(order.items()).hasSize(2);
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
        assertThat(controller.page(20, 2, null).getContent()).isEmpty();
    }

    @Test
    void summaryIncludesHistoricApprovedRevenueButOnlySevenLocalDaysInChart() {
        var zone = ZoneId.of("America/Argentina/Buenos_Aires");
        var today = LocalDate.now(zone);
        em.createNativeQuery("update customer_orders set created_at = :created where id = :id")
                .setParameter("created", today.minusDays(8).atStartOfDay(zone).toInstant())
                .setParameter("id", orderIds.getFirst()).executeUpdate();
        em.createNativeQuery("update customer_orders set created_at = :created where id = :id")
                .setParameter("created", today.atStartOfDay(zone).toInstant())
                .setParameter("id", orderIds.get(1)).executeUpdate();
        var summary = new AdminOrderController(orders, mock(OrderStockService.class)).summary();
        assertThat(summary.soldOrders()).isEqualTo(2);
        assertThat(summary.revenue()).isEqualByComparingTo("40");
        assertThat(summary.averageTicket()).isEqualByComparingTo("20");
        assertThat(summary.activeOrders()).isEqualTo(7);
        assertThat(summary.statusCounts()).containsEntry("PAID", 2L).containsEntry("PENDING_PAYMENT", 5L);
        assertThat(summary.salesChart()).hasSize(7);
        assertThat(summary.salesChart().getLast().total()).isEqualByComparingTo("20");
        assertThat(summary.salesChart().getLast().height()).isEqualTo(100);
        assertThat(summary.salesChart().subList(0, 6)).allSatisfy(day -> {
            assertThat(day.total()).isZero();
            assertThat(day.height()).isEqualTo(4);
        });
        assertThat(summary.recentOrders()).hasSize(5)
                .noneSatisfy(order -> assertThat(order.id()).isEqualTo(orderIds.getFirst()));
    }

    @Test
    void inventorySearchFetchesNamesAndSummaryOnlyCountsActiveVariants() {
        var baseline = inventory.summarizeActive();
        statistics.clear();
        var page = inventory.findActivePage("READ BRAND", PageRequest.of(0, 3)).map(InventoryPageResponse::from);
        assertThat(page.getTotalElements()).isEqualTo(7);
        assertThat(page.getContent()).hasSize(3).allSatisfy(row -> {
            assertThat(row.productName()).startsWith("Read product");
            assertThat(row.brandName()).startsWith("Read brand");
            assertThat(row.colorName()).isEqualTo("Blue");
        });
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        var removed = inventory.findActivePage("Read product 0", PageRequest.of(0, 20)).getContent().getFirst();
        removed.getVariant().deactivate();
        var removedProduct = inventory.findActivePage("Read product 6", PageRequest.of(0, 20)).getContent().getFirst();
        removedProduct.getVariant().getProduct().deactivate();
        em.flush();
        var summary = inventory.summarizeActive();
        assertThat(summary.lowStock()).isEqualTo(baseline.lowStock() - 1);
        assertThat(summary.availableUnits()).isEqualTo(baseline.availableUnits() - 6);
        assertThat(inventory.findActivePage("Read product", PageRequest.of(0, 20)).getTotalElements()).isEqualTo(5);
    }

    @Test
    void proofListHasNoNPlusOneAndPreviewLookupOnlySelectsRequestedKey() {
        var loaded = proofs.findByStatusOrderBySubmittedAtAsc(BankTransferProofStatus.PENDING_REVIEW);
        assertThat(loaded.stream().map(AdminBankTransferProofResponse::from).toList()).hasSize(7)
                .allSatisfy(proof -> assertThat(proof.previewCount()).isEqualTo(2));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        var proofId = loaded.getFirst().getId();
        em.clear();
        statistics.clear();
        assertThat(proofs.findPreviewStorageKey(proofId, 1)).contains("preview-0-1");
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(statistics.getCollectionLoadCount()).isZero();
        assertThat(proofs.findPreviewStorageKey(proofId, 2)).isEmpty();
    }
}
