package com.computerstore.catalog.controller;

import com.computerstore.catalog.domain.*;
import com.computerstore.catalog.dto.*;
import com.computerstore.catalog.repository.*;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
public class CatalogAdminController {
    private final ProductRepository products; private final CategoryRepository categories; private final BrandRepository brands; private final InventoryRepository inventory;
    public CatalogAdminController(ProductRepository products, CategoryRepository categories, BrandRepository brands, InventoryRepository inventory) { this.products=products; this.categories=categories; this.brands=brands; this.inventory=inventory; }
    @GetMapping("/categories") public List<CategoryResponse> listCategories() { return categories.findAll().stream().filter(Category::isActive).map(c -> new CategoryResponse(c.getId(), c.getName(), c.getSlug())).toList(); }
    @GetMapping("/brands") public List<BrandResponse> listBrands() { return brands.findAll().stream().filter(Brand::isActive).map(b -> new BrandResponse(b.getId(), b.getName())).toList(); }
    @PostMapping("/products") @Transactional public ResponseEntity<ProductSummaryResponse> createProduct(@Valid @RequestBody CreateProductRequest request) { Product p=products.save(new Product(request.name().trim(),request.slug().trim(),request.description().trim(),request.price(),category(request.categoryId()),brand(request.brandId()))); inventory.save(new Inventory(p)); return ResponseEntity.status(HttpStatus.CREATED).body(product(p)); }
    @PutMapping("/products/{id}") public ProductSummaryResponse updateProduct(@PathVariable Long id,@Valid @RequestBody UpdateProductRequest request) { Product p=products.findById(id).orElseThrow(()->new ResourceNotFoundException("Product not found.")); p.update(request.name().trim(),request.slug().trim(),request.description().trim(),request.price(),category(request.categoryId()),brand(request.brandId())); return product(products.save(p)); }
    @DeleteMapping("/products/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteProduct(@PathVariable Long id) { Product p=products.findById(id).orElseThrow(()->new ResourceNotFoundException("Product not found.")); p.deactivate(); products.save(p); }
    @PostMapping("/categories") public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest r) { Category c=categories.save(new Category(r.name().trim(),r.slug().trim())); return ResponseEntity.status(HttpStatus.CREATED).body(new CategoryResponse(c.getId(),c.getName(),c.getSlug())); }
    @PutMapping("/categories/{id}") public CategoryResponse updateCategory(@PathVariable Long id,@Valid @RequestBody CategoryRequest r) { Category c=category(id); c.update(r.name().trim(),r.slug().trim()); categories.save(c); return new CategoryResponse(c.getId(),c.getName(),c.getSlug()); }
    @DeleteMapping("/categories/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCategory(@PathVariable Long id) { Category c=category(id); c.deactivate(); categories.save(c); }
    @PostMapping("/brands") public ResponseEntity<BrandResponse> createBrand(@Valid @RequestBody BrandRequest r) { Brand b=brands.save(new Brand(r.name().trim())); return ResponseEntity.status(HttpStatus.CREATED).body(new BrandResponse(b.getId(),b.getName())); }
    @PutMapping("/brands/{id}") public BrandResponse updateBrand(@PathVariable Long id,@Valid @RequestBody BrandRequest r) { Brand b=brand(id); b.update(r.name().trim()); brands.save(b); return new BrandResponse(b.getId(),b.getName()); }
    @DeleteMapping("/brands/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteBrand(@PathVariable Long id) { Brand b=brand(id); b.deactivate(); brands.save(b); }
    private Category category(Long id) { return categories.findById(id).filter(Category::isActive).orElseThrow(()->new ResourceNotFoundException("Category not found.")); }
    private Brand brand(Long id) { return brands.findById(id).filter(Brand::isActive).orElseThrow(()->new ResourceNotFoundException("Brand not found.")); }
    private ProductSummaryResponse product(Product p) { return new ProductSummaryResponse(p.getId(),p.getName(),p.getSlug(),p.getDescription(),p.getPrice(),p.getCategory().getId(),p.getCategory().getName(),p.getBrand().getId(),p.getBrand().getName()); }
}
