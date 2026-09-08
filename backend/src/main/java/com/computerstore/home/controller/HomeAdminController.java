package com.computerstore.home.controller;

import com.computerstore.home.domain.HomeBannerDevice;
import com.computerstore.home.dto.AdminHomeSectionResponse;
import com.computerstore.home.dto.HomeBannerResponse;
import com.computerstore.home.dto.HomeSectionOrderRequest;
import com.computerstore.home.dto.HomeSectionRequest;
import com.computerstore.home.service.HomeSectionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/admin/home/sections")
@PreAuthorize("hasRole('ADMIN')")
public class HomeAdminController {
    private final HomeSectionService service;

    public HomeAdminController(HomeSectionService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminHomeSectionResponse> sections() {
        return service.adminSections();
    }

    @PostMapping
    public ResponseEntity<AdminHomeSectionResponse> create(@Valid @RequestBody HomeSectionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminHomeSectionResponse update(@PathVariable Long id,
            @Valid @RequestBody HomeSectionRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody HomeSectionOrderRequest request) {
        service.reorder(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping(value = "/{id}/banners/{device}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public HomeBannerResponse uploadBanner(@PathVariable Long id, @PathVariable HomeBannerDevice device,
            @RequestPart("file") MultipartFile file) {
        return service.uploadBanner(id, device, file);
    }

    @DeleteMapping("/{id}/banners/{device}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBanner(@PathVariable Long id, @PathVariable HomeBannerDevice device) {
        service.deleteBanner(id, device);
    }
}
