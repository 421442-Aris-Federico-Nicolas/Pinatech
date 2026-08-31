package com.computerstore.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.computerstore.inventory.repository.InventoryRepository;
import org.junit.jupiter.api.Test;

class ProductControllerTest {

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
        when(products.findById(1L)).thenReturn(Optional.of(product));
        when(product.isActive()).thenReturn(true);
        when(product.getId()).thenReturn(1L);
        when(product.getCategory()).thenReturn(category);
        when(product.getBrand()).thenReturn(brand);
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
    }
}
