package com.computerstore.shipping.controller;

import com.computerstore.shipping.service.ZipnovaWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shipping/webhooks/zipnova")
public class ZipnovaWebhookController {
    private final ZipnovaWebhookService service;
    public ZipnovaWebhookController(ZipnovaWebhookService service) { this.service = service; }
    @PostMapping("/{secret}")
    public ResponseEntity<Void> webhook(@PathVariable String secret, @RequestBody JsonNode payload) {
        service.accept(secret, payload); return ResponseEntity.ok().build();
    }
}
