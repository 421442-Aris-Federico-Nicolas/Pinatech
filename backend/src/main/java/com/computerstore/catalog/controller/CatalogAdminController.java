package com.computerstore.catalog.controller;

import com.computerstore.catalog.domain.*;
import com.computerstore.catalog.dto.*;
import com.computerstore.catalog.repository.*;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
import com.computerstore.order.repository.CustomerOrderRepository;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@RestController
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
public class CatalogAdminController {
    private final ProductRepository products; private final CategoryRepository categories; private final BrandRepository brands; private final ProductSpecificationRepository specifications; private final ProductVariantRepository variants; private final ProductImageRepository images; private final InventoryRepository inventory; private final CustomerOrderRepository orders; private final ProductImageService productImages;
    public CatalogAdminController(ProductRepository products, CategoryRepository categories, BrandRepository brands, ProductSpecificationRepository specifications, ProductVariantRepository variants, ProductImageRepository images, InventoryRepository inventory, CustomerOrderRepository orders, ProductImageService productImages) { this.products=products; this.categories=categories; this.brands=brands; this.specifications=specifications; this.variants=variants; this.images=images; this.inventory=inventory; this.orders=orders; this.productImages=productImages; }
    @GetMapping("/categories") public List<CategoryResponse> listCategories() { return categories.findAll().stream().filter(Category::isActive).map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug())).toList(); }
    @GetMapping("/brands") public List<BrandResponse> listBrands() { return brands.findAll().stream().filter(Brand::isActive).map(b -> new BrandResponse(b.getId(), b.getName())).toList(); }
    @PostMapping("/products") @Transactional public ResponseEntity<ProductSummaryResponse> createProduct(@Valid @RequestBody CreateProductRequest request) { validateVariantNames(request.variants()); if (request.variants().stream().anyMatch(item->item.id()!=null || item.imageId()!=null)) throw new InvalidRequestException("New product variants cannot include an id or image id."); Product p=products.save(new Product(request.name().trim(),request.slug().trim(),request.description().trim(),request.price(),category(request.categoryId()),brand(request.brandId()))); saveSpecifications(p, request.specifications()); createVariants(p, request.variants()); return ResponseEntity.status(HttpStatus.CREATED).body(product(p)); }
    @PutMapping("/products/{id}") @Transactional public ProductSummaryResponse updateProduct(@PathVariable Long id,@Valid @RequestBody UpdateProductRequest request) { validateVariantNames(request.variants()); Product p=products.findByIdForUpdate(id).orElseThrow(()->new ResourceNotFoundException("Product not found.")); validateVariantImages(id, request.variants()); p.update(request.name().trim(),request.slug().trim(),request.description().trim(),request.price(),category(request.categoryId()),brand(request.brandId())); products.save(p); specifications.deleteByProductId(id); saveSpecifications(p, request.specifications()); syncVariants(p, request.variants()); return product(p); }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable Long id) { Product p=products.findById(id).orElseThrow(()->new ResourceNotFoundException("Product not found.")); p.deactivate(); products.save(p); }
    @PostMapping(value = "/products/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProductImageResponse> uploadProductImage(
            @PathVariable Long id,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "altText", required = false) String altText) {
        return ResponseEntity.status(HttpStatus.CREATED).body(productImages.upload(id, file, altText));
    }
    @DeleteMapping("/products/{productId}/images/{imageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductImage(@PathVariable Long productId, @PathVariable Long imageId) { productImages.delete(productId, imageId); }
    @PostMapping("/categories") public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest r) { Category c=categories.save(new Category(r.name().trim(),r.slug().trim())); return ResponseEntity.status(HttpStatus.CREATED).body(new CategoryResponse(c.getId(),c.getName(),c.getSlug())); }
    @PutMapping("/categories/{id}") public CategoryResponse updateCategory(@PathVariable Long id,@Valid @RequestBody CategoryRequest r) { Category c=category(id); c.update(r.name().trim(),r.slug().trim()); categories.save(c); return new CategoryResponse(c.getId(),c.getName(),c.getSlug()); }
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deleteCategory(@PathVariable Long id) { Category c=category(id); if (products.existsByCategory_IdAndActiveTrue(id)) throw new BusinessRuleException("Category cannot be deleted while it has active products."); c.deactivate(); categories.save(c); }
    @PostMapping("/brands") public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody BrandRequest r) { Brand b=brands.save(new Brand(r.name().trim())); return ResponseEntity.status(HttpStatus.CREATED).body(new BrandResponse(b.getId(),b.getName())); }
    @PutMapping("/brands/{id}") public BrandResponse updateBrand(@PathVariable Long id,@Valid @RequestBody BrandRequest r) { Brand b=brand(id); b.update(r.name().trim()); brands.save(b); return new BrandResponse(b.getId(),b.getName()); }
    @DeleteMapping("/brands/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional public void deleteBrand(@PathVariable Long id) { Brand b=brand(id); if (products.existsByBrand_IdAndActiveTrue(id)) throw new BusinessRuleException("Brand cannot be deleted while it has active products."); b.deactivate(); brands.save(b); }
    private Category category(Long id) { return categories.findById(id).filter(Category::isActive).orElseThrow(()->new ResourceNotFoundException("Category not found.")); }
    private Brand brand(Long id) { return brands.findById(id).filter(Brand::isActive).orElseThrow(()->new ResourceNotFoundException("Brand not found.")); }
    private void saveSpecifications(Product product, List<ProductSpecificationRequest> requested) { specifications.saveAll(java.util.stream.IntStream.range(0, requested.size()).mapToObj(index -> { ProductSpecificationRequest item=requested.get(index); return new ProductSpecification(product,item.groupName().trim(),item.name().trim(),item.value().trim(),item.highlighted(),index); }).toList()); }
    private List<ProductSpecificationResponse> specificationResponses(Long productId) { return specifications.findByProduct_IdOrderByDisplayOrderAscIdAsc(productId).stream().map(item -> new ProductSpecificationResponse(item.getId(),item.getGroupName(),item.getName(),item.getValue(),item.isHighlighted(),item.getDisplayOrder())).toList(); }
    private void createVariants(Product product, List<ProductVariantRequest> requested) { for (int index=0; index<requested.size(); index++) { ProductVariantRequest item=requested.get(index); ProductVariant variant=variants.save(new ProductVariant(product,item.colorName().trim(),normalizedHex(item.colorHex()),null,index)); inventory.save(new Inventory(variant)); } }
    private void syncVariants(Product product, List<ProductVariantRequest> requested) { List<ProductVariant> current=variants.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(product.getId()); java.util.Map<Long,ProductVariant> byId=current.stream().collect(java.util.stream.Collectors.toMap(ProductVariant::getId,item->item)); Set<Long> retained=new HashSet<>(); for (ProductVariantRequest item:requested) { if (item.id()!=null && (byId.get(item.id())==null || !retained.add(item.id()))) throw new InvalidRequestException("Invalid product variant."); } for (ProductVariant variant:current) { if (retained.contains(variant.getId())) continue; Inventory stock=inventory.findByVariantIdForUpdate(variant.getId()).orElseThrow(()->new ResourceNotFoundException("Inventory not found.")); if (stock.getAvailableQuantity()!=0 || stock.getReservedQuantity()!=0 || orders.existsActiveByVariantId(variant.getId())) throw new BusinessRuleException("A color with stock or active orders cannot be removed."); } for (ProductVariant variant:current) variant.update("__internal_variant_update_"+java.util.UUID.randomUUID(),variant.getColorHex(),variant.getImageId(),variant.getDisplayOrder()); variants.flush(); for (int index=0; index<requested.size(); index++) { ProductVariantRequest item=requested.get(index); if (item.id()==null) { ProductVariant created=variants.save(new ProductVariant(product,item.colorName().trim(),normalizedHex(item.colorHex()),item.imageId(),index)); inventory.save(new Inventory(created)); } else byId.get(item.id()).update(item.colorName().trim(),normalizedHex(item.colorHex()),item.imageId(),index); } for (ProductVariant variant:current) if (!retained.contains(variant.getId())) variant.deactivate(); }
    private void validateVariantNames(List<ProductVariantRequest> requested) { Set<String> names=new HashSet<>(); for (ProductVariantRequest item:requested) if (!names.add(item.colorName().trim().toLowerCase(Locale.ROOT))) throw new InvalidRequestException("Product colors cannot be repeated."); }
    private void validateVariantImages(Long productId, List<ProductVariantRequest> requested) { Set<Long> imageIds=requested.stream().map(ProductVariantRequest::imageId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()); if (!imageIds.isEmpty() && images.countByProductIdAndIdIn(productId,imageIds)!=imageIds.size()) throw new InvalidRequestException("Invalid product variant image."); }
    private String normalizedHex(String value) { return value==null || value.isBlank() ? null : value.toUpperCase(Locale.ROOT); }
    private List<ProductVariantResponse> variantResponses(Long productId) { return variants.findByProduct_IdAndActiveTrueOrderByDisplayOrderAscIdAsc(productId).stream().map(item -> { Inventory stock=inventory.findById(item.getId()).orElseThrow(()->new ResourceNotFoundException("Inventory not found.")); return new ProductVariantResponse(item.getId(),item.getColorName(),item.getColorHex(),item.getImageId(),stock.getAvailableQuantity()>0,stock.getAvailableQuantity()); }).toList(); }
    private ProductSummaryResponse product(Product p) { return new ProductSummaryResponse(p.getId(),p.getName(),p.getSlug(),p.getDescription(),p.getPrice(),p.getCategory().getId(),p.getCategory().getName(),p.getBrand().getId(),p.getBrand().getName(),productImages.responsesForProduct(p.getId()),specificationResponses(p.getId()),variantResponses(p.getId())); }
}
