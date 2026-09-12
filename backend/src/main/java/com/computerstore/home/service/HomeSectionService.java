package com.computerstore.home.service;

import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.dto.ProductListItemResponse;
import com.computerstore.catalog.repository.CategoryRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.home.domain.HomeBanner;
import com.computerstore.home.domain.HomeBannerDevice;
import com.computerstore.home.domain.HomeSection;
import com.computerstore.home.domain.HomeSectionMode;
import com.computerstore.home.dto.AdminHomeSectionResponse;
import com.computerstore.home.dto.HomeBannerResponse;
import com.computerstore.home.dto.HomeCategoryResponse;
import com.computerstore.home.dto.HomeSectionOrderRequest;
import com.computerstore.home.dto.HomeSectionRequest;
import com.computerstore.home.dto.HomeSectionResponse;
import com.computerstore.home.repository.HomeBannerRepository;
import com.computerstore.home.repository.HomeSectionReadRepository;
import com.computerstore.home.repository.HomeSectionRepository;
import com.computerstore.storage.LocalImageStorage;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HomeSectionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeSectionService.class);
    private static final int MAX_SECTIONS = 6;
    private static final int MAX_MANUAL_PRODUCTS = 24;
    private static final int MAX_AUTOMATIC_CATEGORIES = 20;

    private final HomeSectionRepository sections;
    private final HomeBannerRepository banners;
    private final HomeSectionReadRepository reads;
    private final CategoryRepository categories;
    private final ProductRepository products;
    private final LocalImageStorage storage;
    private final HomeConfigurationLockService configurationLock;

    public HomeSectionService(HomeSectionRepository sections, HomeBannerRepository banners,
            HomeSectionReadRepository reads, CategoryRepository categories, ProductRepository products,
            LocalImageStorage storage, HomeConfigurationLockService configurationLock) {
        this.sections = sections;
        this.banners = banners;
        this.reads = reads;
        this.categories = categories;
        this.products = products;
        this.storage = storage;
        this.configurationLock = configurationLock;
    }

    @Transactional(readOnly = true)
    public List<HomeSectionResponse> publicSections() {
        Map<Long, List<HomeCategoryResponse>> categoriesBySection = reads.activeSectionCategories().stream()
                .collect(Collectors.groupingBy(HomeSectionReadRepository.CategoryRow::sectionId,
                        LinkedHashMap::new,
                        Collectors.mapping(HomeSectionReadRepository.CategoryRow::category, Collectors.toList())));
        Map<Long, List<com.computerstore.catalog.dto.ProductListItemResponse>> productsBySection =
                reads.eligibleProducts().stream().collect(Collectors.groupingBy(
                        HomeSectionReadRepository.ProductRow::sectionId, LinkedHashMap::new,
                        Collectors.mapping(HomeSectionReadRepository.ProductRow::product, Collectors.toList())));

        return reads.activeSections().stream()
                .filter(section -> !productsBySection.getOrDefault(section.id(), List.of()).isEmpty())
                .map(section -> new HomeSectionResponse(section.id(), section.displayOrder(), section.eyebrow(),
                        section.title(), section.description(), section.buttonLabel(), section.mode(),
                        section.productLimit(), section.sort(), section.bannerDesktopUrl(), section.bannerMobileUrl(),
                        categoriesBySection.getOrDefault(section.id(), List.of()),
                        productsBySection.getOrDefault(section.id(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminHomeSectionResponse> adminSections() {
        Map<Long, List<Long>> categoryIdsBySection = reads.allSectionCategoryIds().stream()
                .collect(Collectors.groupingBy(HomeSectionReadRepository.SectionCategoryIdRow::sectionId,
                        LinkedHashMap::new,
                        Collectors.mapping(HomeSectionReadRepository.SectionCategoryIdRow::categoryId,
                                Collectors.toList())));
        Map<Long, List<ProductListItemResponse>> productsBySection = reads.allConfiguredProducts().stream()
                .collect(Collectors.groupingBy(HomeSectionReadRepository.ProductRow::sectionId,
                        LinkedHashMap::new,
                        Collectors.mapping(HomeSectionReadRepository.ProductRow::product, Collectors.toList())));
        return reads.allSections().stream().map(section -> {
            List<ProductListItemResponse> configuredProducts = productsBySection.getOrDefault(section.id(), List.of());
            return new AdminHomeSectionResponse(section.id(), section.displayOrder(), section.eyebrow(),
                    section.title(), section.description(), section.buttonLabel(), section.mode(),
                    section.productLimit(), section.sort(),
                    categoryIdsBySection.getOrDefault(section.id(), List.of()),
                    configuredProducts.stream().map(ProductListItemResponse::id).toList(), configuredProducts,
                    section.active(), section.bannerDesktopUrl(), section.bannerMobileUrl());
        }).toList();
    }

    @Transactional
    public AdminHomeSectionResponse create(HomeSectionRequest request) {
        configurationLock.lock();
        if (sections.count() >= MAX_SECTIONS) {
            throw new BusinessRuleException("The home cannot have more than 6 sections.");
        }
        Configuration configuration = configuration(request);
        HomeSection section = new HomeSection(sections.maximumDisplayOrder() + 1);
        apply(section, request, configuration);
        HomeSection saved = sections.saveAndFlush(section);
        return adminSection(saved.getId());
    }

    @Transactional
    public AdminHomeSectionResponse update(Long id, HomeSectionRequest request) {
        configurationLock.lock();
        HomeSection section = sections.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home section not found."));
        Configuration configuration = configuration(request);
        apply(section, request, configuration);
        sections.saveAndFlush(section);
        return adminSection(id);
    }

    @Transactional
    public void reorder(HomeSectionOrderRequest request) {
        configurationLock.lock();
        List<HomeSection> current = sections.findAllForUpdate();
        List<Long> requested = request.sectionIds();
        Set<Long> unique = new HashSet<>(requested);
        Set<Long> existing = current.stream().map(HomeSection::getId).collect(Collectors.toSet());
        if (requested.size() != current.size() || unique.size() != requested.size() || !unique.equals(existing)) {
            throw new InvalidRequestException("sectionIds must contain every home section id exactly once.");
        }
        Map<Long, HomeSection> byId = current.stream()
                .collect(Collectors.toMap(HomeSection::getId, Function.identity()));
        for (int index = 0; index < requested.size(); index++) {
            byId.get(requested.get(index)).setDisplayOrder(index);
        }
        sections.flush();
    }

    @Transactional
    public void delete(Long id) {
        configurationLock.lock();
        HomeSection section = sections.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home section not found."));
        List<String> storageKeys = section.getBanners().stream().map(HomeBanner::getStorageKey)
                .filter(java.util.Objects::nonNull).toList();
        sections.delete(section);
        sections.flush();
        storageKeys.forEach(this::deleteAfterCommit);
    }

    @Transactional
    public HomeBannerResponse uploadBanner(Long sectionId, HomeBannerDevice device, MultipartFile file) {
        HomeSection section = sections.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Home section not found."));
        HomeBanner previous = banner(section, device);
        LocalImageStorage.StoredImage stored = storage.storeWebp(file);
        cleanupOnRollback(stored.storageKey());
        try {
            if (previous != null) {
                section.removeBanner(previous);
                banners.delete(previous);
                banners.flush();
            }
            HomeBanner replacement = banners.saveAndFlush(new HomeBanner(section, device, stored));
            section.addBanner(replacement);
            if (previous != null && previous.getStorageKey() != null) {
                deleteAfterCommit(previous.getStorageKey());
            }
            return new HomeBannerResponse(replacement.getId(), device, adminBannerUrl(replacement));
        } catch (RuntimeException exception) {
            try {
                storage.delete(stored.storageKey());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    @Transactional
    public void deleteBanner(Long sectionId, HomeBannerDevice device) {
        HomeSection section = sections.findByIdForUpdate(sectionId)
                .orElseThrow(() -> new ResourceNotFoundException("Home section not found."));
        HomeBanner banner = banner(section, device);
        if (banner == null) {
            throw new ResourceNotFoundException("Home banner not found.");
        }
        section.removeBanner(banner);
        banners.delete(banner);
        banners.flush();
        if (banner.getStorageKey() != null) {
            deleteAfterCommit(banner.getStorageKey());
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BannerContent publicBannerContent(Long bannerId) {
        HomeBanner banner = banners.findByIdAndSectionActiveTrue(bannerId)
                .orElseThrow(() -> new ResourceNotFoundException("Home banner not found."));
        return bannerContent(banner);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public BannerContent adminBannerContent(Long bannerId) {
        HomeBanner banner = banners.findById(bannerId)
                .orElseThrow(() -> new ResourceNotFoundException("Home banner not found."));
        return bannerContent(banner);
    }

    private BannerContent bannerContent(HomeBanner banner) {
        if (banner.getStorageKey() == null || banner.getContentType() == null
                || banner.getOriginalFilename() == null || banner.getSizeBytes() == null) {
            throw new ResourceNotFoundException("Home banner content not found.");
        }
        var content = storage.publicWebp(banner.getStorageKey());
        return new BannerContent(content.path(), "image/webp", webpFilename(banner.getOriginalFilename()),
                content.sizeBytes());
    }

    private Configuration configuration(HomeSectionRequest request) {
        if (request.mode() == HomeSectionMode.AUTOMATIC) {
            if (request.sort() == null) {
                throw new InvalidRequestException("Automatic sections require a sort value.");
            }
            List<Long> ids = distinctIds(request.categoryIds(), "categoryIds");
            if (ids.isEmpty()) {
                throw new InvalidRequestException("Automatic sections require at least one category.");
            }
            if (ids.size() > MAX_AUTOMATIC_CATEGORIES) {
                throw new InvalidRequestException("Automatic sections cannot have more than 20 categories.");
            }
            Map<Long, Category> found = categories.findAllById(ids).stream()
                    .collect(Collectors.toMap(Category::getId, Function.identity()));
            List<Category> selected = ids.stream().map(found::get).toList();
            if (selected.stream().anyMatch(item -> item == null || !item.isActive())) {
                throw new InvalidRequestException("All configured categories must exist and be active.");
            }
            return new Configuration(selected, List.of());
        }

        List<Long> ids = distinctIds(request.productIds(), "productIds");
        if (ids.isEmpty() || ids.size() > MAX_MANUAL_PRODUCTS) {
            throw new InvalidRequestException("Manual sections require between 1 and 24 products.");
        }
        Map<Long, Product> found = products.findConfigurationCandidates(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> selected = ids.stream().map(found::get).toList();
        if (selected.stream().anyMatch(item -> item == null || !item.isActive()
                || !item.getCategory().isActive() || !item.getBrand().isActive())) {
            throw new InvalidRequestException("All configured products must exist and be active.");
        }
        return new Configuration(List.of(), selected);
    }

    private List<Long> distinctIds(List<Long> ids, String field) {
        if (ids == null) {
            throw new InvalidRequestException(field + " is required.");
        }
        if (new HashSet<>(ids).size() != ids.size()) {
            throw new InvalidRequestException(field + " cannot contain duplicate ids.");
        }
        return List.copyOf(ids);
    }

    private void apply(HomeSection section, HomeSectionRequest request, Configuration configuration) {
        section.configure(normalized(request.eyebrow()), request.title().trim(), normalized(request.description()),
                normalized(request.buttonLabel()), request.mode(), request.productLimit(),
                request.mode() == HomeSectionMode.AUTOMATIC ? request.sort() : null, request.active(),
                configuration.categories(), configuration.products());
    }

    private String normalized(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private AdminHomeSectionResponse adminSection(Long id) {
        return adminSections().stream().filter(section -> section.id().equals(id)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Home section not found."));
    }

    private HomeBanner banner(HomeSection section, HomeBannerDevice device) {
        return section.getBanners().stream().filter(item -> item.getDevice() == device).findFirst().orElse(null);
    }

    private String adminBannerUrl(HomeBanner banner) {
        return "/api/admin/home/banners/" + banner.getId() + "/content";
    }

    private String webpFilename(String filename) {
        int dot = filename.lastIndexOf('.');
        String basename = dot > 0 ? filename.substring(0, dot) : filename;
        return (basename.length() > 250 ? basename.substring(0, 250) : basename) + ".webp";
    }

    private void cleanupOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(storageKey);
                    } catch (RuntimeException ignored) {
                        // Database consistency takes priority; cleanup is best-effort.
                    }
                }
            }
        });
    }

    private void deleteAfterCommit(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    storage.delete(storageKey);
                } catch (RuntimeException exception) {
                    LOGGER.warn("Could not delete committed home banner file with storage key {}.",
                            storageKey, exception);
                }
            }
        });
    }

    private record Configuration(List<Category> categories, List<Product> products) {}
    public record BannerContent(Path path, String contentType, String fileName, long sizeBytes) {}
}
