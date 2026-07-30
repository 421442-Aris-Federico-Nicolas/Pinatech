package com.computerstore.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.repository.BrandRepository;
import com.computerstore.catalog.repository.CategoryRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.catalog.repository.ProductSpecificationRepository;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class CatalogAdminControllerTest {
    private ProductRepository products;
    private CategoryRepository categories;
    private BrandRepository brands;
    private CatalogAdminController controller;

    @BeforeEach
    void setUp() {
        products = Mockito.mock(ProductRepository.class);
        categories = Mockito.mock(CategoryRepository.class);
        brands = Mockito.mock(BrandRepository.class);
        controller = new CatalogAdminController(
                products,
                categories,
                brands,
                Mockito.mock(ProductSpecificationRepository.class),
                Mockito.mock(ProductVariantRepository.class),
                Mockito.mock(InventoryRepository.class),
                Mockito.mock(CustomerOrderRepository.class),
                Mockito.mock(ProductImageService.class));
    }

    @Test
    void rejectsDeletingCategoryUsedByAnActiveProduct() {
        Category category = new Category("Notebooks", "notebooks");
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(products.existsByCategory_IdAndActiveTrue(1L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> controller.deleteCategory(1L));

        verify(categories, never()).save(category);
    }

    @Test
    void rejectsDeletingBrandUsedByAnActiveProduct() {
        Brand brand = new Brand("Pinatech");
        when(brands.findById(2L)).thenReturn(Optional.of(brand));
        when(products.existsByBrand_IdAndActiveTrue(2L)).thenReturn(true);

        assertThrows(BusinessRuleException.class, () -> controller.deleteBrand(2L));

        verify(brands, never()).save(brand);
    }

    @Test
    void deactivatesUnusedCategoryAndBrand() {
        Category category = new Category("Notebooks", "notebooks");
        Brand brand = new Brand("Pinatech");
        when(categories.findById(1L)).thenReturn(Optional.of(category));
        when(brands.findById(2L)).thenReturn(Optional.of(brand));

        controller.deleteCategory(1L);
        controller.deleteBrand(2L);

        assertFalse(category.isActive());
        assertFalse(brand.isActive());
        verify(categories).save(category);
        verify(brands).save(brand);
    }
}
