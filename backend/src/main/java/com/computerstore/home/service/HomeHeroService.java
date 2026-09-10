package com.computerstore.home.service;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.home.domain.HomeHeroImage;
import com.computerstore.home.domain.HomeHeroImageDevice;
import com.computerstore.home.domain.HomeHeroSlide;
import com.computerstore.home.dto.AdminHomeHeroImageResponse;
import com.computerstore.home.dto.AdminHomeHeroSlideResponse;
import com.computerstore.home.dto.HomeHeroImageResponse;
import com.computerstore.home.dto.HomeHeroReorderRequest;
import com.computerstore.home.dto.HomeHeroSlideRequest;
import com.computerstore.home.dto.HomeHeroSlideResponse;
import com.computerstore.home.repository.HomeHeroImageRepository;
import com.computerstore.home.repository.HomeHeroSlideRepository;
import com.computerstore.storage.LocalImageStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class HomeHeroService {
    private static final Logger LOGGER = LoggerFactory.getLogger(HomeHeroService.class);
    private static final int MAX_SLIDES = 5;

    private final HomeHeroSlideRepository slides;
    private final HomeHeroImageRepository images;
    private final LocalImageStorage storage;
    private final HomeConfigurationLockService configurationLock;

    public HomeHeroService(HomeHeroSlideRepository slides, HomeHeroImageRepository images,
            LocalImageStorage storage, HomeConfigurationLockService configurationLock) {
        this.slides = slides;
        this.images = images;
        this.storage = storage;
        this.configurationLock = configurationLock;
    }

    @Transactional(readOnly = true)
    public List<HomeHeroSlideResponse> publicSlides() {
        return slides.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .filter(HomeHeroSlide::isActive)
                .map(this::publicSlide)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AdminHomeHeroSlideResponse> adminSlides() {
        return slides.findAllByOrderByDisplayOrderAscIdAsc().stream()
                .map(this::adminSlide)
                .toList();
    }

    @Transactional
    public AdminHomeHeroSlideResponse create(HomeHeroSlideRequest request) {
        configurationLock.lock();
        if (slides.count() >= MAX_SLIDES) {
            throw new BusinessRuleException("El inicio no puede tener más de 5 slides.");
        }
        HomeHeroSlide slide = new HomeHeroSlide(slides.maximumDisplayOrder() + 1);
        apply(slide, request);
        HomeHeroSlide saved = slides.saveAndFlush(slide);
        return adminSlide(saved.getId());
    }

    @Transactional
    public AdminHomeHeroSlideResponse update(Long id, HomeHeroSlideRequest request) {
        configurationLock.lock();
        HomeHeroSlide slide = slides.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        if (!Boolean.TRUE.equals(request.active()) && slide.isActive() && slides.countByActiveTrue() <= 1) {
            throw new BusinessRuleException("Debe quedar al menos un slide activo.");
        }
        apply(slide, request);
        slides.saveAndFlush(slide);
        return adminSlide(id);
    }

    @Transactional
    public void reorder(HomeHeroReorderRequest request) {
        configurationLock.lock();
        List<HomeHeroSlide> current = slides.findAllForUpdate();
        List<Long> requested = request.slideIds();
        Map<Long, HomeHeroSlide> byId = current.stream()
                .collect(Collectors.toMap(HomeHeroSlide::getId, Function.identity()));
        if (requested.size() != current.size()
                || requested.stream().distinct().count() != requested.size()
                || !requested.stream().allMatch(byId::containsKey)) {
            throw new InvalidRequestException("slideIds must contain every home hero slide id exactly once.");
        }
        for (int index = 0; index < requested.size(); index++) {
            byId.get(requested.get(index)).setDisplayOrder(index);
        }
        slides.flush();
    }

    @Transactional
    public void delete(Long id) {
        configurationLock.lock();
        HomeHeroSlide slide = slides.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        if (slide.isActive() && slides.countByActiveTrue() <= 1) {
            throw new BusinessRuleException("Debe quedar al menos un slide activo.");
        }
        List<String> storageKeys = slide.getImages().stream().map(HomeHeroImage::getStorageKey).toList();
        slides.delete(slide);
        slides.flush();
        storageKeys.forEach(this::deleteAfterCommit);
    }

    @Transactional
    public AdminHomeHeroImageResponse uploadImage(Long slideId, HomeHeroImageDevice device, MultipartFile file) {
        configurationLock.lock();
        HomeHeroSlide slide = slides.findByIdForUpdate(slideId)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        HomeHeroImage previous = image(slide, device);
        LocalImageStorage.StoredImage stored = storage.store(file);
        cleanupOnRollback(stored.storageKey());
        try {
            if (previous != null) {
                slide.removeImage(previous);
                images.delete(previous);
                images.flush();
            }
            HomeHeroImage replacement = images.saveAndFlush(
                    new HomeHeroImage(slide, device, stored, stored.width(), stored.height()));
            slide.addImage(replacement);
            if (previous != null) {
                deleteAfterCommit(previous.getStorageKey());
            }
            return adminImage(replacement);
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
    public void deleteImage(Long slideId, HomeHeroImageDevice device) {
        configurationLock.lock();
        HomeHeroSlide slide = slides.findByIdForUpdate(slideId)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        HomeHeroImage image = image(slide, device);
        if (image == null) {
            throw new ResourceNotFoundException("Home hero image not found.");
        }
        slide.removeImage(image);
        images.delete(image);
        images.flush();
        deleteAfterCommit(image.getStorageKey());
    }

    @Transactional(readOnly = true)
    public ImageContent publicImageContent(Long imageId) {
        HomeHeroImage image = images.findByIdAndSlideActiveTrue(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero image not found."));
        return imageContent(image);
    }

    @Transactional(readOnly = true)
    public ImageContent adminImageContent(Long imageId) {
        HomeHeroImage image = images.findById(imageId)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero image not found."));
        return imageContent(image);
    }

    private HomeHeroSlideResponse publicSlide(Long id) {
        HomeHeroSlide slide = slides.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        return publicSlide(slide);
    }

    private HomeHeroSlideResponse publicSlide(HomeHeroSlide slide) {
        HomeHeroImage desktop = image(slide, HomeHeroImageDevice.DESKTOP);
        HomeHeroImage mobile = image(slide, HomeHeroImageDevice.MOBILE);
        return new HomeHeroSlideResponse(slide.getId(), slide.getDisplayOrder(), slide.getEyebrow(),
                slide.getTitle(), slide.getAccent(), slide.getDescription(), slide.getLink(),
                slide.getLinkLabel(), slide.isShowLoginLink(), slide.getAltText(),
                desktop == null ? null : new HomeHeroImageResponse(desktop.getId(), publicImageUrl(desktop.getId()),
                        desktop.getWidth(), desktop.getHeight()),
                mobile == null ? null : new HomeHeroImageResponse(mobile.getId(), publicImageUrl(mobile.getId()),
                        mobile.getWidth(), mobile.getHeight()));
    }

    private AdminHomeHeroSlideResponse adminSlide(Long id) {
        HomeHeroSlide slide = slides.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Home hero slide not found."));
        return adminSlide(slide);
    }

    private AdminHomeHeroSlideResponse adminSlide(HomeHeroSlide slide) {
        List<AdminHomeHeroImageResponse> imageResponses = slide.getImages().stream()
                .map(this::adminImage)
                .toList();
        return new AdminHomeHeroSlideResponse(slide.getId(), slide.getDisplayOrder(), slide.isActive(),
                slide.getEyebrow(), slide.getTitle(), slide.getAccent(), slide.getDescription(),
                slide.getLink(), slide.getLinkLabel(), slide.isShowLoginLink(), slide.getAltText(),
                imageResponses);
    }

    private AdminHomeHeroImageResponse adminImage(HomeHeroImage image) {
        return new AdminHomeHeroImageResponse(image.getId(), image.getDevice(), adminImageUrl(image.getId()),
                image.getWidth(), image.getHeight(), image.getOriginalFilename());
    }

    private HomeHeroImage image(HomeHeroSlide slide, HomeHeroImageDevice device) {
        return slide.getImages().stream().filter(item -> item.getDevice() == device).findFirst().orElse(null);
    }

    private void apply(HomeHeroSlide slide, HomeHeroSlideRequest request) {
        String link = request.link().trim();
        if (!link.startsWith("/") || link.startsWith("//")) {
            throw new InvalidRequestException("El enlace del botón debe ser una ruta interna de la tienda.");
        }
        slide.configure(request.eyebrow(), request.title().trim(), request.accent(), request.description(),
                link, request.linkLabel(), request.showLoginLink(), request.altText(), request.active());
    }

    private String publicImageUrl(Long imageId) {
        return "/api/home/hero/images/" + imageId + "/content";
    }

    private String adminImageUrl(Long imageId) {
        return "/api/admin/home/hero/images/" + imageId + "/content";
    }

    private ImageContent imageContent(HomeHeroImage image) {
        return new ImageContent(storage.load(image.getStorageKey()), image.getContentType(),
                image.getOriginalFilename(), image.getSizeBytes(), image.getWidth(), image.getHeight());
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
                    LOGGER.warn("Could not delete committed home hero image file with storage key {}.",
                            storageKey, exception);
                }
            }
        });
    }

    public record ImageContent(Path path, String contentType, String fileName, long sizeBytes,
            int width, int height) {}
}
