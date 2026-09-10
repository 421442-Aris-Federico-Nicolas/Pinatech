package com.computerstore.home;

import com.computerstore.config.SecurityConfiguration;
import com.computerstore.home.controller.HomeAdminController;
import com.computerstore.home.controller.HomeAdminBannerController;
import com.computerstore.home.controller.HomeController;
import com.computerstore.home.controller.HomeHeroAdminController;
import com.computerstore.home.controller.HomeHeroController;
import com.computerstore.home.service.HomeHeroService;
import com.computerstore.home.service.HomeSectionService;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.security.CustomUserDetailsService;
import com.computerstore.security.JwtAuthenticationFilter;
import com.computerstore.security.JwtService;
import com.computerstore.security.RestAccessDeniedHandler;
import com.computerstore.security.RestAuthenticationEntryPoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.http.MediaType;

@WebMvcTest({HomeController.class, HomeAdminController.class, HomeAdminBannerController.class,
        HomeHeroController.class, HomeHeroAdminController.class})
@Import({SecurityConfiguration.class, JwtAuthenticationFilter.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
class HomeControllerSecurityTest {
    @MockBean HomeSectionService service;
    @MockBean HomeHeroService heroService;
    @MockBean JwtService jwtService;
    @MockBean CustomUserDetailsService userDetailsService;
    @Autowired MockMvc mvc;

    @Test
    void permitsOnlyTheDeclaredPublicHomeGets() throws Exception {
        when(service.publicSections()).thenReturn(List.of());

        mvc.perform(get("/api/home/sections")).andExpect(status().isOk());
        mvc.perform(get("/api/home/not-public")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/home/sections")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpointsRequireAdminRole() throws Exception {
        when(service.adminSections()).thenReturn(List.of());

        mvc.perform(get("/api/admin/home/sections")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/home/sections").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home/sections").with(user(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void uploadedBannerContentIsPublicAndImmutableForSevenDays(@TempDir Path directory) throws Exception {
        Path file = Files.write(directory.resolve("banner.png"), new byte[]{1, 2, 3});
        when(service.publicBannerContent(5L)).thenReturn(
                new HomeSectionService.BannerContent(file, "image/png", "banner.png", 3));

        mvc.perform(get("/api/home/banners/5/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=604800, public, immutable"))
                .andExpect(header().string("Content-Type", "image/png"));
    }

    @Test
    void adminBannerPreviewRequiresAdminAndUsesTheProtectedContentMethod(@TempDir Path directory) throws Exception {
        Path file = Files.write(directory.resolve("admin-banner.png"), new byte[]{1});
        when(service.adminBannerContent(7L)).thenReturn(
                new HomeSectionService.BannerContent(file, "image/png", "admin-banner.png", 1));

        mvc.perform(get("/api/admin/home/banners/7/content"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/home/banners/7/content").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home/banners/7/content").with(user(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void validatesSectionTitleAndProductLimitBeforeCallingTheAdminService() throws Exception {
        mvc.perform(post("/api/admin/home/sections")
                        .with(user(principal("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eyebrow":null,"title":" ","description":null,"buttonLabel":null,
                                 "mode":"MANUAL","productLimit":13,"sort":null,
                                 "categoryIds":[],"productIds":[1],"active":true}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAutomaticSectionsWithMoreThanTwentyCategories() throws Exception {
        String categoryIds = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(Long::toString).collect(java.util.stream.Collectors.joining(","));

        mvc.perform(post("/api/admin/home/sections")
                        .with(user(principal("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eyebrow":null,"title":"Automatic","description":null,"buttonLabel":null,
                                 "mode":"AUTOMATIC","productLimit":12,"sort":"NAME_ASC",
                                 "categoryIds":[%s],"productIds":[],"active":true}
                                """.formatted(categoryIds)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void heroSlidesArePublicButAdminEndpointsRequireAdminRole() throws Exception {
        when(heroService.publicSlides()).thenReturn(List.of());
        when(heroService.adminSlides()).thenReturn(List.of());

        mvc.perform(get("/api/home/hero")).andExpect(status().isOk());
        mvc.perform(post("/api/admin/home/hero")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/home/hero").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home/hero").with(user(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test
    void publicHeroImageIsImmutableForSevenDaysButAdminPreviewIsNoStore(@TempDir Path directory) throws Exception {
        Path file = Files.write(directory.resolve("hero.png"), new byte[]{1, 2, 3});
        when(heroService.publicImageContent(5L)).thenReturn(
                new HomeHeroService.ImageContent(file, "image/png", "hero.png", 3, 2000, 848));
        when(heroService.adminImageContent(7L)).thenReturn(
                new HomeHeroService.ImageContent(file, "image/png", "admin-hero.png", 3, 720, 512));

        mvc.perform(get("/api/home/hero/images/5/content"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=604800, public, immutable"))
                .andExpect(header().string("Content-Type", "image/png"));

        mvc.perform(get("/api/admin/home/hero/images/7/content"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/home/hero/images/7/content").with(user(principal("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/home/hero/images/7/content").with(user(principal("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void validatesHeroSlideTextBeforeCallingTheAdminService() throws Exception {
        mvc.perform(post("/api/admin/home/hero")
                        .with(user(principal("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eyebrow":"","title":" ","accent":"","description":"","link":"",
                                 "linkLabel":"","showLoginLink":false,"altText":"","active":false}
                                """))
                .andExpect(status().isBadRequest());
    }

    private AuthenticatedUser principal(String role) {
        return new AuthenticatedUser(1L, "user@example.com",
                List.of(new SimpleGrantedAuthority(role)));
    }
}
