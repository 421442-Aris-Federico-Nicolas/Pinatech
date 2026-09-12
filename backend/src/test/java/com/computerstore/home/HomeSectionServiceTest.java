package com.computerstore.home;

import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.repository.CategoryRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.home.domain.HomeBanner;
import com.computerstore.home.domain.HomeBannerDevice;
import com.computerstore.home.domain.HomeSection;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.domain.HomeSectionSort;
import com.computerstore.home.dto.HomeSectionOrderRequest;
import com.computerstore.home.dto.HomeSectionRequest;
import com.computerstore.home.repository.HomeBannerRepository;
import com.computerstore.home.repository.HomeSectionReadRepository;
import com.computerstore.home.repository.HomeSectionRepository;
import com.computerstore.home.service.HomeSectionService;
import com.computerstore.home.service.HomeConfigurationLockService;
import com.computerstore.storage.LocalImageStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeSectionServiceTest {
    @Mock HomeSectionRepository sections;
    @Mock HomeBannerRepository banners;
    @Mock HomeSectionReadRepository reads;
    @Mock CategoryRepository categories;
    @Mock ProductRepository products;
    @Mock LocalImageStorage storage;
    @Mock HomeConfigurationLockService configurationLock;

    private HomeSectionService service;

    @BeforeEach
    void setUp() {
        service = new HomeSectionService(sections, banners, reads, categories, products, storage, configurationLock);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void serializesCreateAndRejectsTheSeventhSectionBeforeResolvingReferences() {
        when(sections.count()).thenReturn(6L);

        HomeSectionRequest request = new HomeSectionRequest(null, "Manual", null, null,
                HomeSectionMode.MANUAL, 12, null, List.of(), List.of(1L), true);
        assertThrows(BusinessRuleException.class, () -> service.create(request));

        InOrder order = inOrder(configurationLock, sections);
        order.verify(configurationLock).lock();
        order.verify(sections).count();
        verify(products, never()).findConfigurationCandidates(any());
    }

    @Test
    void validatesModeSpecificLimitsAndNormalizesForeignFields() {
        assertThrows(InvalidRequestException.class, () -> service.create(new HomeSectionRequest(null, "Auto", null,
                null, HomeSectionMode.AUTOMATIC, 12, HomeSectionSort.NAME_ASC, List.of(), List.of(99L), true)));
        assertThrows(InvalidRequestException.class, () -> service.create(new HomeSectionRequest(null, "Auto", null,
                null, HomeSectionMode.AUTOMATIC, 12, null, List.of(1L), List.of(), true)));
        assertThrows(InvalidRequestException.class, () -> service.create(new HomeSectionRequest(null, "Auto", null,
                null, HomeSectionMode.AUTOMATIC, 12, HomeSectionSort.NAME_ASC,
                LongStream.rangeClosed(1, 21).boxed().toList(), List.of(), true)));
        assertThrows(InvalidRequestException.class, () -> service.create(manual(List.of())));
        assertThrows(InvalidRequestException.class, () -> service.create(manual(
                LongStream.rangeClosed(1, 25).boxed().toList())));
        assertThrows(InvalidRequestException.class, () -> service.create(manual(List.of(1L, 1L))));

        when(sections.count()).thenReturn(0L);
        when(sections.maximumDisplayOrder()).thenReturn(-1);
        Product product = product(1L);
        when(products.findConfigurationCandidates(List.of(1L))).thenReturn(List.of(product));
        when(sections.saveAndFlush(any(HomeSection.class))).thenAnswer(invocation -> {
            HomeSection saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        var card = new com.computerstore.catalog.dto.ProductListItemResponse(1L, "Product", "product",
                BigDecimal.TEN, null, "Category", null, "Brand", List.of(), false);
        when(reads.allSections()).thenReturn(List.of(new HomeSectionReadRepository.AdminSectionRow(
                1L, 0, "Label", "Manual", null, null, HomeSectionMode.MANUAL, 2, null,
                true, null, null)));
        when(reads.allConfiguredProducts()).thenReturn(List.of(new HomeSectionReadRepository.ProductRow(1L, card)));
        HomeSectionRequest request = new HomeSectionRequest("  Label  ", "  Manual  ", "  ", null,
                HomeSectionMode.MANUAL, 2, HomeSectionSort.PRICE_DESC, List.of(999L), List.of(1L), true);

        var response = service.create(request);

        assertThat(response.eyebrow()).isEqualTo("Label");
        assertThat(response.title()).isEqualTo("Manual");
        assertThat(response.description()).isNull();
        assertThat(response.sort()).isNull();
        assertThat(response.categoryIds()).isEmpty();
        assertThat(response.productIds()).containsExactly(1L);
        assertThat(response.products()).containsExactly(card);
        verify(categories, never()).findAllById(any());
    }

    @Test
    void reorderRequiresEveryIdExactlyOnceAndAppliesRequestedOrderAtomically() {
        HomeSection first = section(1L);
        HomeSection second = section(2L);
        when(sections.findAllForUpdate()).thenReturn(List.of(first, second));

        assertThrows(InvalidRequestException.class,
                () -> service.reorder(new HomeSectionOrderRequest(List.of(1L, 1L))));
        service.reorder(new HomeSectionOrderRequest(List.of(2L, 1L)));

        assertThat(second.getDisplayOrder()).isZero();
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        verify(configurationLock, times(2)).lock();
        verify(sections).flush();
    }

    @Test
    void replacementUsesANewBannerIdAndDeletesOldFileOnlyAfterCommit() {
        HomeSection section = section(1L);
        LocalImageStorage.StoredImage oldStored = stored("11111111-1111-1111-1111-111111111111");
        HomeBanner old = new HomeBanner(section, HomeBannerDevice.DESKTOP, oldStored);
        ReflectionTestUtils.setField(old, "id", 10L);
        section.addBanner(old);
        LocalImageStorage.StoredImage replacementStored = stored("22222222-2222-2222-2222-222222222222");
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
        when(sections.findByIdForUpdate(1L)).thenReturn(Optional.of(section));
        when(storage.storeWebp(file)).thenReturn(replacementStored);
        when(banners.saveAndFlush(any(HomeBanner.class))).thenAnswer(invocation -> {
            HomeBanner saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 11L);
            return saved;
        });
        TransactionSynchronizationManager.initSynchronization();

        var response = service.uploadBanner(1L, HomeBannerDevice.DESKTOP, file);

        assertThat(response.url()).isEqualTo("/api/admin/home/banners/11/content");
        verify(storage, never()).delete(oldStored.storageKey());
        synchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete(oldStored.storageKey());
        verify(storage, never()).delete(replacementStored.storageKey());
    }

    @Test
    void uploadRollbackDeletesOnlyTheNewFileAndDeleteWaitsForCommit() {
        HomeSection section = section(1L);
        LocalImageStorage.StoredImage uploaded = stored("33333333-3333-3333-3333-333333333333");
        MockMultipartFile file = new MockMultipartFile("file", new byte[]{1});
        when(sections.findByIdForUpdate(1L)).thenReturn(Optional.of(section));
        when(storage.storeWebp(file)).thenReturn(uploaded);
        when(banners.saveAndFlush(any(HomeBanner.class))).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();

        service.uploadBanner(1L, HomeBannerDevice.MOBILE, file);
        synchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

        verify(storage).delete(uploaded.storageKey());
    }

    @Test
    void bannerAndSectionDeletionRemoveFilesOnlyAfterCommit() {
        HomeSection section = section(1L);
        LocalImageStorage.StoredImage stored = stored("44444444-4444-4444-4444-444444444444");
        HomeBanner banner = new HomeBanner(section, HomeBannerDevice.DESKTOP, stored);
        section.addBanner(banner);
        when(sections.findByIdForUpdate(1L)).thenReturn(Optional.of(section));
        TransactionSynchronizationManager.initSynchronization();

        service.deleteBanner(1L, HomeBannerDevice.DESKTOP);

        verify(banners).delete(banner);
        verify(storage, never()).delete(stored.storageKey());
        synchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(storage).delete(stored.storageKey());
    }

    @Test
    void updateAndDeleteAcquireTheSharedConfigurationLockBeforeSectionRows() {
        HomeSection section = section(1L);
        when(sections.findByIdForUpdate(1L)).thenReturn(Optional.of(section));

        assertThrows(InvalidRequestException.class, () -> service.update(1L,
                new HomeSectionRequest(null, "Auto", null, null, HomeSectionMode.AUTOMATIC,
                        12, HomeSectionSort.NAME_ASC, List.of(), List.of(), true)));
        service.delete(1L);

        InOrder order = inOrder(configurationLock, sections);
        order.verify(configurationLock).lock();
        order.verify(sections).findByIdForUpdate(1L);
        order.verify(configurationLock).lock();
        order.verify(sections).findByIdForUpdate(1L);
    }

    @Test
    void inactiveSectionBannerIsHiddenPubliclyButAvailableToAdmin() {
        HomeSection section = section(1L);
        HomeBanner banner = new HomeBanner(section, HomeBannerDevice.DESKTOP,
                stored("55555555-5555-5555-5555-555555555555"));
        ReflectionTestUtils.setField(banner, "id", 5L);
        when(banners.findByIdAndSectionActiveTrue(5L)).thenReturn(Optional.empty());
        when(banners.findById(5L)).thenReturn(Optional.of(banner));
        when(storage.publicWebp(banner.getStorageKey())).thenReturn(
                new LocalImageStorage.StoredContent(java.nio.file.Path.of("banner.webp"), 10));

        assertThrows(ResourceNotFoundException.class, () -> service.publicBannerContent(5L));
        assertThat(service.adminBannerContent(5L).fileName()).isEqualTo("banner.webp");
    }

    @Test
    void adminResponseUsesThreeBatchReadsAndDoesNotLoadSectionEntities() {
        var card = new com.computerstore.catalog.dto.ProductListItemResponse(9L, "Selected", "selected",
                BigDecimal.TEN, 2L, "Category", 3L, "Brand", List.of(), false);
        when(reads.allSectionCategoryIds()).thenReturn(List.of(
                new HomeSectionReadRepository.SectionCategoryIdRow(2L, 2L)));
        when(reads.allConfiguredProducts()).thenReturn(List.of(
                new HomeSectionReadRepository.ProductRow(1L, card)));
        when(reads.allSections()).thenReturn(List.of(
                new HomeSectionReadRepository.AdminSectionRow(1L, 0, null, "Manual", null, null,
                        HomeSectionMode.MANUAL, 12, null, false,
                        "/api/admin/home/banners/5/content", null),
                new HomeSectionReadRepository.AdminSectionRow(2L, 1, null, "Automatic", null, null,
                        HomeSectionMode.AUTOMATIC, 12, HomeSectionSort.NAME_ASC, true, null, null)));

        var response = service.adminSections();

        assertThat(response.getFirst().products()).containsExactly(card);
        assertThat(response.getFirst().productIds()).containsExactly(9L);
        assertThat(response.getLast().products()).isEmpty();
        assertThat(response.getLast().categoryIds()).containsExactly(2L);
        verify(reads).allSections();
        verify(reads).allSectionCategoryIds();
        verify(reads).allConfiguredProducts();
        verifyNoMoreInteractions(reads);
        verifyNoInteractions(sections);
    }

    private HomeSectionRequest manual(List<Long> ids) {
        when(sections.count()).thenReturn(0L);
        return new HomeSectionRequest(null, "Manual", null, null, HomeSectionMode.MANUAL,
                12, null, List.of(), ids, true);
    }

    private Product product(Long id) {
        Category category = new Category("Category", "category");
        Brand brand = new Brand("Brand");
        Product product = new Product("Product", "product", "Description", BigDecimal.TEN,
                category, brand, null, null, null, null, null, false);
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }

    private HomeSection section(Long id) {
        HomeSection section = new HomeSection(0);
        ReflectionTestUtils.setField(section, "id", id);
        return section;
    }

    private LocalImageStorage.StoredImage stored(String key) {
        return new LocalImageStorage.StoredImage(key, "banner.webp", "image/webp", 10, 2000, 848);
    }

    private List<TransactionSynchronization> synchronizations() {
        return TransactionSynchronizationManager.getSynchronizations();
    }
}
