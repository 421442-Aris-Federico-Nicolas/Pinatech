package com.computerstore.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.catalog.repository.ProductSpecificationRepository;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.GlobalExceptionHandler;
import com.computerstore.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;

class ProductControllerTest {

    @Test
    void imageRoutesServeCorrectBytesAndImmutableCacheHeaders(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var images = mock(ProductImageService.class);
        var path = java.nio.file.Files.write(directory.resolve("image"), new byte[]{1, 2, 3});
        when(images.thumbnail(5L)).thenReturn(new ProductImageService.ProductImageContent(path, "image/jpeg", "image-5.jpg", 3));
        when(images.content(5L)).thenReturn(new ProductImageService.ProductImageContent(path, "image/png", "original.png", 3));
        var controller = new ProductController(mock(ProductRepository.class), mock(ProductSpecificationRepository.class),
                mock(ProductVariantRepository.class), mock(InventoryRepository.class), images);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build();
        for (String route : List.of("thumbnail", "content")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/products/images/5/" + route))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().contentType(
                            route.equals("thumbnail") ? "image/jpeg" : "image/png"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().bytes(new byte[]{1, 2, 3}))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Length", "3"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "max-age=604800, public, immutable"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Content-Disposition",
                            org.hamcrest.Matchers.startsWith("inline;")));
        }
    }

    @Test
    void cardsNormalizesSearchBoundsPageAndDoesNotLoadDetailCollections() {
        var products = mock(ProductRepository.class);
        var specifications = mock(ProductSpecificationRepository.class);
        var variants = mock(ProductVariantRepository.class);
        var inventory = mock(InventoryRepository.class);
        var images = mock(ProductImageService.class);
        var controller = new ProductController(products, specifications, variants, inventory, images);
        var pageable = org.springframework.data.domain.PageRequest.of(2, 500,
                org.springframework.data.domain.Sort.by("price").descending());
        controller.cards(" ALPHA ", 2L, null, 3L, java.math.BigDecimal.ONE, java.math.BigDecimal.TEN, pageable);
        org.mockito.Mockito.verify(products).findCards("%alpha%", 2L, List.of(-1L), false, 3L,
                java.math.BigDecimal.ONE,
                java.math.BigDecimal.TEN, org.springframework.data.domain.PageRequest.of(2, 100,
                        pageable.getSort().and(org.springframework.data.domain.Sort.by("id"))));
        controller.cards("  ", null, List.of(), null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 12));
        org.mockito.Mockito.verify(products).findCards(null, null, List.of(-1L), false, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 12, org.springframework.data.domain.Sort.by("id")));
        org.mockito.Mockito.verifyNoInteractions(specifications, variants, inventory, images);
    }

    @Test
    void cardsAcceptsCsvAndRepeatedCategoryIdsAndMultiTakesPrecedence() throws Exception {
        var products = mock(ProductRepository.class);
        when(products.findCards(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> new org.springframework.data.domain.PageImpl<>(List.of(),
                        invocation.getArgument(7), 0));
        var controller = controller(products);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();

        mvc.perform(get("/api/products/cards").param("categoryId", "99").param("categoryIds", "5,8"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(products).findCards(null, null, List.of(5L, 8L), true, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 12,
                        org.springframework.data.domain.Sort.by("name").and(org.springframework.data.domain.Sort.by("id"))));

        mvc.perform(get("/api/products/cards").param("categoryIds", "13", "21"))
                .andExpect(status().isOk());
        org.mockito.Mockito.verify(products).findCards(null, null, List.of(13L, 21L), true, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 12,
                        org.springframework.data.domain.Sort.by("name").and(org.springframework.data.domain.Sort.by("id"))));
    }

    @Test
    void cardsRejectsNonPositiveOrTooManyCategoryIds() throws Exception {
        var products = mock(ProductRepository.class);
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller(products))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new org.springframework.data.web.PageableHandlerMethodArgumentResolver())
                .build();

        mvc.perform(get("/api/products/cards").param("categoryIds", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/products/cards").param("categoryIds", "-1"))
                .andExpect(status().isBadRequest());
        String[] tooManyIds = java.util.stream.LongStream.rangeClosed(1, 21)
                .mapToObj(Long::toString).toArray(String[]::new);
        mvc.perform(get("/api/products/cards").param("categoryIds", tooManyIds))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(products);
    }

    private ProductController controller(ProductRepository products) {
        return new ProductController(products, mock(ProductSpecificationRepository.class),
                mock(ProductVariantRepository.class), mock(InventoryRepository.class), mock(ProductImageService.class));
    }

    @Test
    void publicDetailExposesVariantImageId() {
        ProductRepository products = mock(ProductRepository.class);
        ProductSpecificationRepository specifications = mock(ProductSpecificationRepository.class);
        ProductVariantRepository variants = mock(ProductVariantRepository.class);
        InventoryRepository inventory = mock(InventoryRepository.class);
        ProductImageService images = mock(ProductImageService.class);
        Product product = mock(Product.class);
        ProductVariant variant = mock(ProductVariant.class);
        Category category = mock(Category.class);
        Brand brand = mock(Brand.class);
        when(products.findDetailById(1L)).thenReturn(Optional.of(product));
        when(product.isActive()).thenReturn(true);
        when(product.getId()).thenReturn(1L);
        when(product.getCategory()).thenReturn(category);
        when(product.getBrand()).thenReturn(brand);
        when(product.getShippingWeightGrams()).thenReturn(500);
        when(product.getShippingHeightCm()).thenReturn(10);
        when(product.getShippingWidthCm()).thenReturn(20);
        when(product.getShippingLengthCm()).thenReturn(30);
        when(product.getShippingClassificationId()).thenReturn(2);
        when(product.isMustKeepVertical()).thenReturn(true);
        when(variant.getId()).thenReturn(7L);
        when(variant.getProduct()).thenReturn(product);
        when(variant.getImageId()).thenReturn(9L);
        when(variants.findActiveByProductIds(List.of(1L))).thenReturn(List.of(variant));
        when(inventory.findAllById(List.of(7L))).thenReturn(List.of());
        when(images.responsesForProduct(1L)).thenReturn(List.of());
        when(specifications.findByProduct_IdOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of());
        ProductController controller = new ProductController(products, specifications, variants, inventory, images);

        var response = controller.detail(1L);

        assertEquals(9L, response.variants().getFirst().imageId());
        assertEquals(500, response.shippingWeightGrams());
        assertEquals(2, response.shippingClassificationId());
        assertEquals(true, response.mustKeepVertical());
    }
}
