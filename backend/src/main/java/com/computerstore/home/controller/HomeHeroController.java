package com.computerstore.home.controller;

import com.computerstore.home.dto.HomeHeroSlideResponse;
import com.computerstore.home.service.HomeHeroService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        var content = service.publicImageContent(imageId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}