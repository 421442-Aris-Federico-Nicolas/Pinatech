package com.computerstore.home;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.home.domain.HomeHeroImageDevice;
import com.computerstore.home.dto.HomeHeroReorderRequest;
import com.computerstore.home.dto.HomeHeroSlideRequest;
import com.computerstore.home.repository.HomeHeroImageRepository;
import com.computerstore.home.repository.HomeHeroSlideRepository;
import com.computerstore.home.service.HomeConfigurationLockService;
import com.computerstore.home.service.HomeHeroService;
import com.computerstore.storage.LocalImageStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HomeHeroIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired HomeHeroSlideRepository slides;
    @Autowired HomeHeroImageRepository images;
    @Autowired HomeConfigurationLockService configurationLock;
    @TempDir Path directory;

    private HomeHeroService service;

    @BeforeEach
    void setUp() {
        service = new HomeHeroService(slides, images, new LocalImageStorage(directory.toString()),
                configurationLock);
    }

    @Test
    void migrationSeedsTheTwoCurrentSlidesInOrder() {
        var titles = jdbc.queryForList("SELECT title FROM home_hero_slides ORDER BY display_order");
        assertThat(titles).extracting("title")
                .containsExactly("Elevá tu setup.", "No dejes que tu carrito");
        var activeCount = (Long) jdbc.queryForMap(
                "SELECT COUNT(*) AS total FROM home_hero_slides WHERE is_active").get("total");
        assertThat(activeCount).isEqualTo(2L);
    }

    @Test
    void rejectsTheSixthSlideAtTheDatabaseLevel() {
        whenNoSlides();
        for (int order = 0; order < 5; order++) {
            jdbc.update("""
                    INSERT INTO home_hero_slides (display_order, is_active, eyebrow, title, accent, description,
                        link, link_label, show_login_link, alt_text)
                    VALUES (?, TRUE, 'Eyebrow', 'Título ' || ?, 'Accent', 'Descripción',
                        '/catalog', 'Ir', FALSE, 'Texto alternativo')
                    """, order, order);
        }
        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> jdbc.update("""
                        INSERT INTO home_hero_slides (display_order, is_active, eyebrow, title, accent, description,
                            link, link_label, show_login_link, alt_text)
                        VALUES (5, TRUE, 'Eyebrow', 'Título extra', 'Accent', 'Descripción',
                            '/catalog', 'Ir', FALSE, 'Texto alternativo')
                        """));
    }

    @Test
    void keepsAtLeastOneActiveSlideAcrossMutation() throws Exception {
        whenNoSlides();
        var first = service.create(request("Primera", "/catalog"));
        var second = service.create(request("Segunda", "/cart"));
        service.uploadImage(first.id(), HomeHeroImageDevice.DESKTOP, jpeg("hero.jpg", 2000, 848));
        service.update(first.id(), request("Primera", "/catalog", true));

        assertThrows(BusinessRuleException.class, () -> service.delete(first.id()));
        assertThrows(BusinessRuleException.class,
                () -> service.update(first.id(), request("Primera", "/catalog", false)));
        assertThat(service.publicSlides()).extracting("title").containsExactly("Primera");
    }

    @Test
    void anActiveSlideCanBePublishedBeforeItsImages() throws Exception {
        whenNoSlides();
        var created = service.create(request("Inactiva", "/catalog"));

        var active = service.update(created.id(), request("Activa", "/catalog", true));
        assertThat(active.active()).isTrue();

        service.uploadImage(created.id(), HomeHeroImageDevice.DESKTOP, jpeg("hero.jpg", 2000, 848));
        service.deleteImage(created.id(), HomeHeroImageDevice.DESKTOP);
        assertThat(service.adminSlides().get(0).images()).isEmpty();
    }

    @Test
    void uploadingReplacesThePreviousImageForTheDevice() throws Exception {
        whenNoSlides();
        var created = service.create(request("Slide", "/catalog"));
        var first = service.uploadImage(created.id(), HomeHeroImageDevice.DESKTOP, jpeg("one.jpg", 2000, 848));
        var second = service.uploadImage(created.id(), HomeHeroImageDevice.DESKTOP, jpeg("two.jpg", 1200, 600));

        assertThat(first.id()).isNotEqualTo(second.id());
        var admin = service.adminSlides().get(0);
        assertThat(admin.images()).extracting("id").containsExactly(second.id());
    }

    @Test
    void reordersSlidesByRequestedIds() {
        whenNoSlides();
        var a = service.create(request("A", "/catalog"));
        var b = service.create(request("B", "/cart"));

        service.reorder(new HomeHeroReorderRequest(List.of(b.id(), a.id())));

        assertThat(service.adminSlides()).extracting("id").containsExactly(b.id(), a.id());
    }

    @Test
    void publicSlidesOnlyExposeActiveSlidesWithResolvedImageUrls() throws Exception {
        whenNoSlides();
        var a = service.create(request("A", "/catalog"));
        service.uploadImage(a.id(), HomeHeroImageDevice.DESKTOP, jpeg("hero.jpg", 2000, 848));
        service.update(a.id(), request("A", "/catalog", true));
        service.create(request("B", "/cart"));

        var publicSlides = service.publicSlides();

        assertThat(publicSlides).extracting("title").containsExactly("A");
        assertThat(publicSlides.get(0).desktopImage().url()).isEqualTo(
                "/api/home/hero/images/%d/content?v=webp-1".formatted(publicSlides.get(0).desktopImage().id()));
        assertThat(publicSlides.get(0).desktopImage().width()).isEqualTo(2000);
    }

    private void whenNoSlides() {
        if (slides.count() > 0) {
            jdbc.update("DELETE FROM home_hero_images");
            jdbc.update("DELETE FROM home_hero_slides");
        }
    }

    private HomeHeroSlideRequest request(String title, String link) {
        return request(title, link, false);
    }

    private HomeHeroSlideRequest request(String title, String link, boolean active) {
        return new HomeHeroSlideRequest("Eyebrow", title, "Accent", "Descripción del slide.",
                link, "Ir", false, "Texto alternativo", active);
    }

    private MockMultipartFile jpeg(String name, int width, int height) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "jpg", bytes);
        return new MockMultipartFile("file", name, "image/jpeg", bytes.toByteArray());
    }
}
