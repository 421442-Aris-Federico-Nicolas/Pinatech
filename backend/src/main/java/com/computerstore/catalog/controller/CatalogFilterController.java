package com.computerstore.catalog.controller;

import java.util.List;
import com.computerstore.catalog.dto.BrandResponse;
import com.computerstore.catalog.dto.CategoryResponse;
import com.computerstore.catalog.domain.Brand;
import com.computerstore.catalog.domain.Category;
import com.computerstore.catalog.repository.BrandRepository;
import com.computerstore.catalog.repository.CategoryRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CatalogFilterController {
    private final CategoryRepository categories;
    private final BrandRepository brands;
    public CatalogFilterController(CategoryRepository categories, BrandRepository brands) { this.categories = categories; this.brands = brands; }
    @GetMapping("/categories") public List<CategoryResponse> categories() { return categories.findAll().stream().filter(Category::isActive).map(category -> new CategoryResponse(category.getId(), category.getName(), category.getSlug())).toList(); }
    @GetMapping("/brands") public List<BrandResponse> brands() { return brands.findAll().stream().filter(Brand::isActive).map(brand -> new BrandResponse(brand.getId(), brand.getName())).toList(); }
}
