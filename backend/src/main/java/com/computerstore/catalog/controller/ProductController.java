package com.computerstore.catalog.controller;

import java.math.BigDecimal;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.dto.ProductSummaryResponse;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.ResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductRepository repository;
    private final ProductImageService productImages;
    public ProductController(ProductRepository repository, ProductImageService productImages) { this.repository = repository; this.productImages = productImages; }
    @GetMapping public Page<ProductSummaryResponse> list(@RequestParam(required = false) String search, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long brandId, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice, @PageableDefault(size = 12, sort = "name") Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (search != null && !search.isBlank()) spec = spec.and((r,q,cb) -> cb.like(cb.lower(r.get("name")), "%" + search.trim().toLowerCase() + "%"));
        if (categoryId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("category").get("id"), categoryId));
        if (brandId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("brand").get("id"), brandId));
        if (minPrice != null) spec = spec.and((r,q,cb) -> cb.greaterThanOrEqualTo(r.get("price"), minPrice));
        if (maxPrice != null) spec = spec.and((r,q,cb) -> cb.lessThanOrEqualTo(r.get("price"), maxPrice));
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        Page<Product> products = repository.findAll(spec, boundedPageable);
        Map<Long, List<com.computerstore.catalog.dto.ProductImageResponse>> images = productImages.responsesByProductIds(
                products.getContent().stream().map(Product::getId).toList());
        return products.map(product -> toResponse(product, images.getOrDefault(product.getId(), List.of())));
    }
    @GetMapping("/{id}") public ProductSummaryResponse detail(@PathVariable Long id) {
        Product product = repository.findById(id).filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return toResponse(product, productImages.responsesForProduct(id));
    }

    @GetMapping("/images/{imageId}/content")
    public ResponseEntity<Resource> imageContent(@PathVariable Long imageId) {
        var content = productImages.content(imageId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }

    private ProductSummaryResponse toResponse(Product p, List<com.computerstore.catalog.dto.ProductImageResponse> images) {
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getPrice(),
                p.getCategory().getId(), p.getCategory().getName(), p.getBrand().getId(), p.getBrand().getName(), images);
    }
}
