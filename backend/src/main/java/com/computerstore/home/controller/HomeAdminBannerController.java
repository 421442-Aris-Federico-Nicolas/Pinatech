package com.computerstore.home.controller;

import com.computerstore.home.service.HomeSectionService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/admin/home/banners")
@PreAuthorize("hasRole('ADMIN')")
public class HomeAdminBannerController {
    private final HomeSectionService service;

    public HomeAdminBannerController(HomeSectionService service) {
        this.service = service;
    }

    @GetMapping("/{bannerId}/content")
    public ResponseEntity<Resource> bannerContent(@PathVariable Long bannerId) {
        var content = service.adminBannerContent(bannerId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl("private, no-store");
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}
