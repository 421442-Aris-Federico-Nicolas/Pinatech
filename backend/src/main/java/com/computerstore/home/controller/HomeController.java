package com.computerstore.home.controller;

import com.computerstore.home.dto.HomeSectionResponse;
import com.computerstore.home.service.HomeSectionService;
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
@RequestMapping("/api/home")
public class HomeController {
    private final HomeSectionService service;

    public HomeController(HomeSectionService service) {
        this.service = service;
    }

    @GetMapping("/sections")
    public List<HomeSectionResponse> sections() {
        return service.publicSections();
    }

    @GetMapping("/banners/{bannerId}/content")
    public ResponseEntity<Resource> bannerContent(@PathVariable Long bannerId) {
        var content = service.publicBannerContent(bannerId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic().immutable());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}
