package com.computerstore.catalog.controller;

import java.math.BigDecimal;
import com.computerstore.catalog.domain.Product;
import com.computerstore.catalog.dto.ProductSummaryResponse;
import com.computerstore.catalog.repository.ProductRepository;
import com.computerstore.catalog.repository.ProductSpecificationRepository;
import com.computerstore.catalog.repository.ProductVariantRepository;
import com.computerstore.catalog.service.ProductImageService;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.inventory.domain.Inventory;
import com.computerstore.inventory.repository.InventoryRepository;
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
    private final ProductSpecificationRepository specifications;
    private final ProductVariantRepository variants;
    private final InventoryRepository inventory;
    private final ProductImageService productImages;
    public ProductController(ProductRepository repository, ProductSpecificationRepository specifications, ProductVariantRepository variants, InventoryRepository inventory, ProductImageService productImages) { this.repository = repository; this.specifications = specifications; this.variants = variants; this.inventory = inventory; this.productImages = productImages; }
    @GetMapping public Page<ProductSummaryResponse> list(@RequestParam(required = false) String search, @RequestParam(required = false) Long categoryId, @RequestParam(required = false) Long brandId, @RequestParam(required = false) BigDecimal minPrice, @RequestParam(required = false) BigDecimal maxPrice, @PageableDefault(size = 12, sort = "name") Pageable pageable) {
        Specification<Product> spec = (root, query, cb) -> cb.isTrue(root.get("active"));
        if (search != null && !search.isBlank()) spec = spec.and((r,q,cb) -> cb.like(cb.lower(r.get("name")), "%" + search.trim().toLowerCase() + "%"));
        if (categoryId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("category").get("id"), categoryId));
        if (brandId != null) spec = spec.and((r,q,cb) -> cb.equal(r.get("brand").get("id"), brandId));
        if (minPrice != null) spec = spec.and((r,q,cb) -> cb.greaterThanOrEqualTo(r.get("price"), minPrice));
        if (maxPrice != null) spec = spec.and((r,q,cb) -> cb.lessThanOrEqualTo(r.get("price"), maxPrice));
        Pageable boundedPageable = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), 100), pageable.getSort());
        Page<Product> products = repository.findAll(spec, boundedPageable);
        List<Long> productIds = products.getContent().stream().map(Product::getId).toList();
        Map<Long, List<com.computerstore.catalog.dto.ProductImageResponse>> images = productImages.responsesByProductIds(
                productIds);
        Map<Long, List<com.computerstore.catalog.dto.ProductSpecificationResponse>> productSpecifications = productIds.isEmpty()
                ? Map.of()
                : specificationResponses(specifications.findOrderedByProductIds(productIds));
        Map<Long, List<com.computerstore.catalog.dto.ProductVariantResponse>> productVariants = variantResponses(productIds);
        return products.map(product -> toResponse(product, images.getOrDefault(product.getId(), List.of()),
                productSpecifications.getOrDefault(product.getId(), List.of()), productVariants.getOrDefault(product.getId(), List.of())));
    }
    @GetMapping("/{id}") public ProductSummaryResponse detail(@PathVariable Long id) {
        Product product = repository.findById(id).filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found."));
        return toResponse(product, productImages.responsesForProduct(id), specificationResponses(id), variantResponses(List.of(id)).getOrDefault(id,List.of()));
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

    private ProductSummaryResponse toResponse(Product p, List<com.computerstore.catalog.dto.ProductImageResponse> images, List<com.computerstore.catalog.dto.ProductSpecificationResponse> productSpecifications, List<com.computerstore.catalog.dto.ProductVariantResponse> productVariants) {
        return new ProductSummaryResponse(p.getId(), p.getName(), p.getSlug(), p.getDescription(), p.getPrice(),
                p.getCategory().getId(), p.getCategory().getName(), p.getBrand().getId(), p.getBrand().getName(), images, productSpecifications, productVariants);
    }

    private List<com.computerstore.catalog.dto.ProductSpecificationResponse> specificationResponses(Long productId) {
        return specifications.findByProduct_IdOrderByDisplayOrderAscIdAsc(productId).stream().map(this::specificationResponse).toList();
    }

    private Map<Long, List<com.computerstore.catalog.dto.ProductSpecificationResponse>> specificationResponses(List<com.computerstore.catalog.domain.ProductSpecification> items) {
        return items.stream().collect(java.util.stream.Collectors.groupingBy(
                item -> item.getProduct().getId(),
                java.util.LinkedHashMap::new,
                java.util.stream.Collectors.mapping(this::specificationResponse, java.util.stream.Collectors.toList())));
    }

    private com.computerstore.catalog.dto.ProductSpecificationResponse specificationResponse(com.computerstore.catalog.domain.ProductSpecification item) {
        return new com.computerstore.catalog.dto.ProductSpecificationResponse(item.getId(), item.getGroupName(), item.getName(), item.getValue(), item.isHighlighted(), item.getDisplayOrder());
    }

    private Map<Long,List<com.computerstore.catalog.dto.ProductVariantResponse>> variantResponses(List<Long> productIds) {
        if (productIds.isEmpty()) return Map.of();
        var activeVariants=variants.findActiveByProductIds(productIds);
        Map<Long,Inventory> stock=inventory.findAllById(activeVariants.stream().map(com.computerstore.catalog.domain.ProductVariant::getId).toList()).stream().collect(java.util.stream.Collectors.toMap(Inventory::getVariantId,item->item));
        return activeVariants.stream().collect(java.util.stream.Collectors.groupingBy(item->item.getProduct().getId(),java.util.LinkedHashMap::new,java.util.stream.Collectors.mapping(item->{ Inventory itemStock=stock.get(item.getId()); return new com.computerstore.catalog.dto.ProductVariantResponse(item.getId(),item.getColorName(),item.getColorHex(),itemStock!=null && itemStock.getAvailableQuantity()>0); },java.util.stream.Collectors.toList())));
    }
}
