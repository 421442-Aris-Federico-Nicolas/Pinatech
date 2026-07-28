package com.computerstore.catalog.controller;

import java.math.BigDecimal;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.dto.ProductSummaryResponse;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.common.exception.ResourceNotFoundException;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository repository;
    public ProductController(ProductRepository repository) { this.repository = repository; }
    @GetMapping public Page<ProductSummaryResponse> list(@RequestParam(required = false) String search, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long brandId, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice, @PageableDefault(size = 12, sort = "name") Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (search != null && !search.isBlank()) spec = spec.and((r,q,cb) -> cb.like(cb.lower(r.get("name")), "%" + search.trim().toLowerCase() + "%"));
        if (categoryId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("category").get("id"), categoryId));
        if (brandId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("brand").get("id"), brandId));
        if (minPrice != null) spec = spec.and((r,q,cb) -> cb.greaterThanOrEqualTo(r.get("price"), minPrice));
        if (maxPrice != null) spec = spec.and((r,q,cb) -> cb.lessThanOrEqualTo(r.get("price"), maxPrice));
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        return repository.findAll(spec, boundedPageable).map(this::toResponse);
    }
    @GetMapping("/{id}") public ProductSummaryResponse detail(@PathVariable Long id) { return toResponse(repository.findById(id).filter(Product::isActive).orElseThrow(() -> new ResourceNotFoundException("Product not found."))); }
    private ProductSummaryResponse toResponse(Product p) { return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getPrice(), p.getCategory().getId(), p.getCategory().getName(), p.getBrand().getId(), p.getBrand().getName()); }
}
