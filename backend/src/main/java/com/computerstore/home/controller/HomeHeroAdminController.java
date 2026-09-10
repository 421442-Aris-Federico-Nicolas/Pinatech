package com.computerstore.home.controller;

import com.computerstore.home.domain.HomeHeroImageDevice;
import com.computerstore.home.dto.AdminHomeHeroImageResponse;
import com.computerstore.home.dto.AdminHomeHeroSlideResponse;
import com.computerstore.home.dto.HomeHeroReorderRequest;
import com.computerstore.home.dto.HomeHeroSlideRequest;
import com.computerstore.home.service.HomeHeroService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/admin/home/hero")
@PreAuthorize("hasRole('ADMIN')")
public class HomeHeroAdminController {
    private final HomeHeroService service;

    public HomeHeroAdminController(HomeHeroService service) {
        this.service = service;
    }

    @GetMapping
    public List<AdminHomeHeroSlideResponse> slides() {
        return service.adminSlides();
    }

    @PostMapping
    public ResponseEntity<AdminHomeHeroSlideResponse> create(@Valid @RequestBody HomeHeroSlideRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PutMapping("/{id}")
    public AdminHomeHeroSlideResponse update(@PathVariable Long id, @Valid @RequestBody HomeHeroSlideRequest request) {
        return service.update(id, request);
    }

    @PutMapping("/order")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(@Valid @RequestBody HomeHeroReorderRequest request) {
        service.reorder(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PutMapping(value = "/{id}/images/{device}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AdminHomeHeroImageResponse uploadImage(@PathVariable Long id, @PathVariable HomeHeroImageDevice device,
            @RequestPart("file") MultipartFile file) {
        return service.uploadImage(id, device, file);
    }

    @DeleteMapping("/{id}/images/{device}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteImage(@PathVariable Long id, @PathVariable HomeHeroImageDevice device) {
        service.deleteImage(id, device);
    }

    @GetMapping("/images/{imageId}/content")
    public ResponseEntity<Resource> imageContent(@PathVariable Long imageId) {
        var content = service.adminImageContent(imageId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("private, no-store");
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}