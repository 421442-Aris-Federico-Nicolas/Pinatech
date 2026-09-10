package com.computerstore.home;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.home.domain.HomeHeroImage;
import com.computerstore.home.domain.HomeHeroImageDevice;
import com.computerstore.home.domain.HomeHeroSlide;
import com.computerstore.home.dto.HomeHeroReorderRequest;
import com.computerstore.home.dto.HomeHeroSlideRequest;
import com.computerstore.home.repository.HomeHeroImageRepository;
import com.computerstore.home.repository.HomeHeroSlideRepository;
import com.computerstore.home.service.HomeConfigurationLockService;
import com.computerstore.home.service.HomeHeroService;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HomeHeroServiceTest {
    @Mock HomeHeroSlideRepository slides;
    @Mock HomeHeroImageRepository images;
    @Mock LocalImageStorage storage;
    @Mock HomeConfigurationLockService configurationLock;

    private HomeHeroService service;

    @BeforeEach
    void setUp() {
        service = new HomeHeroService(slides, images, storage, configurationLock);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void rejectsTheSixthSlideBeforeApplying() {
        when(slides.count()).thenReturn(5L);

        assertThrows(BusinessRuleException.class, () -> service.create(request(true)));

        InOrder order = inOrder(configurationLock, slides);
        order.verify(configurationLock).lock();
        order.verify(slides).count();
        verify(images, never()).saveAndFlush(any());
    }

    @Test
    void createsAnActiveSlideBeforeItHasImages() {
        when(slides.count()).thenReturn(1L);
        when(slides.maximumDisplayOrder()).thenReturn(1);
        when(slides.saveAndFlush(any(HomeHeroSlide.class))).thenAnswer(invocation -> {
            HomeHeroSlide savedSlide = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedSlide, "id", 3L);
            return savedSlide;
        });
        when(slides.findById(3L)).thenReturn(Optional.of(new HomeHeroSlide(2)));
        ReflectionTestUtils.setField(slides.findById(3L).orElseThrow(), "id", 3L);

        service.create(request(true));
        verify(slides).saveAndFlush(any(HomeHeroSlide.class));
    }

    @Test
    void createsAnInactiveSlideAtTheEnd() {
        when(slides.count()).thenReturn(1L);
        when(slides.maximumDisplayOrder()).thenReturn(0);
        when(slides.saveAndFlush(any(HomeHeroSlide.class))).thenAnswer(invocation -> {
            HomeHeroSlide savedSlide = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedSlide, "id", 3L);
            return savedSlide;
        });
        when(slides.findById(3L)).thenReturn(Optional.of(new HomeHeroSlide(1)));
        ReflectionTestUtils.setField(slides.findById(3L).orElseThrow(), "id", 3L);

        service.create(request(false));
        verify(slides).saveAndFlush(any(HomeHeroSlide.class));
    }

    @Test
    void allowsActivatingASlideWithoutImages() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));
        when(slides.findById(1L)).thenReturn(Optional.of(slide));
        when(slides.saveAndFlush(any(HomeHeroSlide.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.update(1L, request(true));
        assertThat(slide.isActive()).isTrue();
    }

    @Test
    void rejectsDeactivatingTheLastActiveSlide() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        slide.setActive(true);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));
        when(slides.countByActiveTrue()).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.update(1L, request(false)));
    }

    @Test
    void rejectsExternalOrProtocolRelativeLinks() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));

        assertThrows(InvalidRequestException.class,
                () -> service.update(1L, withLink(request(false), "https://example.com")));
        assertThrows(InvalidRequestException.class,
                () -> service.update(1L, withLink(request(false), "//example.com")));
    }

    @Test
    void reorderRequiresEveryIdExactlyOnce() {
        when(slides.findAllForUpdate()).thenReturn(List.of());
        assertThrows(InvalidRequestException.class, () -> service.reorder(new HomeHeroReorderRequest(List.of(1L))));

        HomeHeroSlide existing1 = new HomeHeroSlide(0);
        HomeHeroSlide existing2 = new HomeHeroSlide(1);
        ReflectionTestUtils.setField(existing1, "id", 1L);
        ReflectionTestUtils.setField(existing2, "id", 2L);
        when(slides.findAllForUpdate()).thenReturn(List.of(existing1, existing2));
        assertThrows(InvalidRequestException.class, () -> service.reorder(new HomeHeroReorderRequest(List.of(1L))));
    }

    @Test
    void reordersSlidesByRequestedSequence() {
        HomeHeroSlide first = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(first, "id", 1L);
        HomeHeroSlide second = new HomeHeroSlide(1);
        ReflectionTestUtils.setField(second, "id", 2L);
        when(slides.findAllForUpdate()).thenReturn(List.of(first, second));

        service.reorder(new HomeHeroReorderRequest(List.of(2L, 1L)));
        assertThat(first.getDisplayOrder()).isEqualTo(1);
        assertThat(second.getDisplayOrder()).isEqualTo(0);
    }

    @Test
    void rejectsDeletingTheLastActiveSlide() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        slide.setActive(true);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));
        when(slides.countByActiveTrue()).thenReturn(1L);

        assertThrows(BusinessRuleException.class, () -> service.delete(1L));
        verify(slides, never()).delete(any());
    }

    @Test
    void uploadImageReplacesThePreviousImageForTheDevice() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));

        LocalImageStorage.StoredImage stored = new LocalImageStorage.StoredImage("key-1", "hero.jpg",
                "image/jpeg", 2048, 2000, 848);
        when(storage.store(any())).thenReturn(stored);
        when(images.saveAndFlush(any())).thenAnswer(invocation -> {
            HomeHeroImage saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 77L);
            return saved;
        });

        var response = service.uploadImage(1L, HomeHeroImageDevice.DESKTOP, new MockMultipartFile(
                "file", "hero.jpg", "image/jpeg", new byte[]{1, 2, 3}));

        assertThat(response.id()).isEqualTo(77L);
        assertThat(response.url()).isEqualTo("/api/admin/home/hero/images/77/content");
        assertThat(response.width()).isEqualTo(2000);
        assertThat(response.height()).isEqualTo(848);
        assertThat(slide.getImages()).hasSize(1);
    }

    @Test
    void uploadImageDeletesTheReplacedImageStorageAfterCommit() throws Exception {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        HomeHeroImage previous = new HomeHeroImage(slide, HomeHeroImageDevice.DESKTOP,
                new LocalImageStorage.StoredImage("old-key", "old.jpg", "image/jpeg", 1, 100, 100), 100, 100);
        ReflectionTestUtils.setField(previous, "id", 9L);
        slide.addImage(previous);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));
        when(storage.store(any())).thenReturn(new LocalImageStorage.StoredImage(
                "new-key", "new.jpg", "image/jpeg", 2, 2000, 848));
        when(images.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadImage(1L, HomeHeroImageDevice.DESKTOP, new MockMultipartFile(
                    "file", "new.jpg", "image/jpeg", new byte[]{1}));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        verify(storage, never()).delete("new-key");
    }

    @Test
    void removesTheLastImageOfAnActiveSlide() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        slide.setActive(true);
        HomeHeroImage image = new HomeHeroImage(slide, HomeHeroImageDevice.DESKTOP,
                new LocalImageStorage.StoredImage("key", "a.jpg", "image/jpeg", 1, 100, 100), 100, 100);
        slide.addImage(image);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));

        service.deleteImage(1L, HomeHeroImageDevice.DESKTOP);
        assertThat(slide.getImages()).isEmpty();
        verify(images).delete(image);
    }

    @Test
    void removesTheLastImageOfAnInactiveSlide() {
        HomeHeroSlide slide = new HomeHeroSlide(0);
        ReflectionTestUtils.setField(slide, "id", 1L);
        HomeHeroImage image = new HomeHeroImage(slide, HomeHeroImageDevice.DESKTOP,
                new LocalImageStorage.StoredImage("key", "a.jpg", "image/jpeg", 1, 100, 100), 100, 100);
        slide.addImage(image);
        when(slides.findByIdForUpdate(1L)).thenReturn(Optional.of(slide));

        service.deleteImage(1L, HomeHeroImageDevice.DESKTOP);
        assertThat(slide.getImages()).isEmpty();
        verify(images).delete(image);
    }

    @Test
    void publicSlidesIsOrderedAndOnlyContainsActiveSlides() {
        HomeHeroSlideRequest request = request(false);
        HomeHeroSlide active = new HomeHeroSlide(0);
        active.configure(request.eyebrow(), "Elevá tu setup.", request.accent(), request.description(),
                request.link(), request.linkLabel(), request.showLoginLink(), request.altText(), true);
        ReflectionTestUtils.setField(active, "id", 1L);
        HomeHeroSlide hidden = new HomeHeroSlide(1);
        ReflectionTestUtils.setField(hidden, "id", 2L);
        when(slides.findAllByOrderByDisplayOrderAscIdAsc()).thenReturn(List.of(active, hidden));

        var response = service.publicSlides();

        assertThat(response).extracting("title").containsExactly("Elevá tu setup.");
        assertThat(response.get(0).desktopImage()).isNull();
    }

    @Test
    void publicImageContentRequiresAnActiveSlide() {
        when(images.findByIdAndSlideActiveTrue(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.publicImageContent(1L));
    }

    private HomeHeroSlideRequest request(boolean active) {
        return new HomeHeroSlideRequest("Eyebrow", "Título", "Accent", "Descripción del slide.",
                "/catalog", "Ir", false, "Texto alternativo", active);
    }

    private HomeHeroSlideRequest withLink(HomeHeroSlideRequest base, String link) {
        // text fields are required; build a fresh request with the desired link.
        return new HomeHeroSlideRequest(base.eyebrow(), base.title(), base.accent(), base.description(),
                link, base.linkLabel(), base.showLoginLink(), base.altText(), base.active());
    }
}
