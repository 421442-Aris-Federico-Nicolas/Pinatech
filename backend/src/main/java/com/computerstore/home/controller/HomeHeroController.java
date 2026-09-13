package com.computerstore.home.controller;

import com.computerstore.home.dto.HomeHeroSlideResponse;
import com.computerstore.home.service.HomeHeroService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RestController
@RequestMapping("/api/home/hero")
public class HomeHeroController {
    private final HomeHeroService service;

    public HomeHeroController(HomeHeroService service) {
        this.service = service;
    }

    @GetMapping
    public List<HomeHeroSlideResponse> slides() {
        return service.publicSlides();
    }

    @GetMapping("/images/{imageId}/content")
    public ResponseEntity<Resource> imageContent(@PathVariable Long imageId) {
        return imageResponse(service.publicImageContent(imageId), Duration.ofDays(7));
    }

    @GetMapping("/images/{imageId}/{width}.webp")
    public ResponseEntity<Resource> responsiveImageContent(@PathVariable Long imageId, @PathVariable int width) {
        return imageResponse(service.publicImageContent(imageId, width), Duration.ofDays(365));
    }

    @GetMapping("/current/{device}/{width}.webp")
    public ResponseEntity<Void> currentPrimaryImage(
            @PathVariable com.computerstore.home.domain.HomeHeroImageDevice device,
            @PathVariable int width) {
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
                .location(URI.create(service.currentPrimaryImageUrl(device, width)))
                .cacheControl(CacheControl.noStore())
                .build();
    }

    private ResponseEntity<Resource> imageResponse(HomeHeroService.ImageContent content, Duration maxAge) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.maxAge(maxAge).cachePublic().immutable());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}
