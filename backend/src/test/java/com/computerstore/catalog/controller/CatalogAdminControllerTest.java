package com.computerstore.catalog.controller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.domain.ProductVariant;
import com.computerstore.catalog.dto.CreateProductRequest;
import com.computerstore.catalog.dto.ProductVariantRequest;
import com.computerstore.catalog.dto.ProductVariantResponse;
import com.computerstore.catalog.dto.UpdateProductRequest;
import com.computerstore.catalog.repository.BrandRepository;
import com.computerstore.catalog.repository.CategoryRepository;
import com.computerstore.catalog.repository.ProductImageRepository;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.catalog.repository.ProductSpecificationRepository;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.order.repository.CustomerOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

class CatalogAdminControllerTest {
    private ProductRepository products;
    private CategoryRepository categories;
    private BrandRepository brands;
    private ProductImageRepository images;
    private ProductVariantRepository variants;
    private InventoryRepository inventory;
    private CatalogAdminController controller;

    @BeforeEach
    void setUp() {
        products = Mockito.mock(ProductRepository.class);
        categories = Mockito.mock(CategoryRepository.class);
        brands = Mockito.mock(BrandRepository.class);
        images = Mockito.mock(ProductImageRepository.class);
        variants = Mockito.mock(ProductVariantRepository.class);
        inventory = Mockito.mock(InventoryRepository.class);
        controller = new CatalogAdminController(
                products,
                categories,
                brands,
                Mockito.mock(ProductSpecificationRepository.class),
                variants,
                images,
                inventory,
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

    @Test
    void rejectsImageReferencesWhenCreatingAProduct() {
        CreateProductRequest request = new CreateProductRequest("Mouse", "mouse", "Mouse gamer",
                BigDecimal.TEN, 1L, 1L, 500, 10, 20, 30, 1, false, List.of(),
                List.of(new ProductVariantRequest(null, "Black", "#000000", 9L)));

        assertThrows(InvalidRequestException.class, () -> controller.createProduct(request));

        verify(products, never()).save(any(Product.class));
    }

    @Test
    void rejectsVariantImagesThatDoNotBelongToTheUpdatedProduct() {
        Product product = Mockito.mock(Product.class);
        when(products.findByIdForUpdate(1L)).thenReturn(Optional.of(product));
        when(images.countByProductIdAndIdIn(1L, java.util.Set.of(9L))).thenReturn(0L);

        assertThrows(InvalidRequestException.class,
                () -> controller.updateProduct(1L, updateRequest(new ProductVariantRequest(null, "Black", "#000000", 9L))));

        verify(products, never()).save(any(Product.class));
    }

    @Test
    void syncPreservesTheCurrentImageDuringTemporaryRenameAndPersistsTheRequestedImage() {
        Product product = Mockito.mock(Product.class);
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        when(product.getId()).thenReturn(1L);
        when(variant.getId()).thenReturn(7L);
        when(variant.getColorHex()).thenReturn("#000000");
        when(variant.getImageId()).thenReturn(11L);
        when(variant.getDisplayOrder()).thenReturn(0);
        when(variants.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of(variant));

        ReflectionTestUtils.invokeMethod(controller, "syncVariants", product,
                List.of(new ProductVariantRequest(7L, "Blue", "#0000ff", 12L)));

        var ordered = Mockito.inOrder(variant, variants);
        ordered.verify(variant).update(Mockito.startsWith("__internal_variant_update_"),
                Mockito.eq("#000000"), Mockito.eq(11L), Mockito.eq(0));
        ordered.verify(variants).flush();
        ordered.verify(variant).update("Blue", "#0000FF", 12L, 0);
    }

    @Test
    void deactivatingVariantClearsItsImageReference() {
        ProductVariant variant = new ProductVariant(Mockito.mock(Product.class), "Black", "#000000", 9L, 0);

        variant.deactivate();

        assertFalse(variant.isActive());
        assertNull(variant.getImageId());
    }

    @Test
    void adminVariantResponseExposesImageId() {
        ProductVariant variant = Mockito.mock(ProductVariant.class);
        Inventory stock = Mockito.mock(Inventory.class);
        when(variant.getId()).thenReturn(7L);
        when(variant.getImageId()).thenReturn(9L);
        when(variants.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(1L)).thenReturn(List.of(variant));
        when(inventory.findById(7L)).thenReturn(Optional.of(stock));

        List<ProductVariantResponse> responses = ReflectionTestUtils.invokeMethod(controller, "variantResponses", 1L);

        assertEquals(9L, responses.getFirst().imageId());
    }

    private UpdateProductRequest updateRequest(ProductVariantRequest variant) {
        return new UpdateProductRequest("Mouse", "mouse", "Mouse gamer", BigDecimal.TEN,
                1L, 1L, 500, 10, 20, 30, 1, false, List.of(), List.of(variant));
    }
}
