package com.computerstore.service.controller;

import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.dto.TicketAttachmentResponse;
import com.computerstore.service.service.TicketAttachmentService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/tickets")
public class TicketAttachmentController {
    private final TicketAttachmentService attachments;

    public TicketAttachmentController(TicketAttachmentService attachments) {
        this.attachments = attachments;
    }

    @GetMapping("/{ticketId}/attachments")
    public List<TicketAttachmentResponse> list(@PathVariable Long ticketId, @AuthenticationPrincipal AuthenticatedUser auth) {
        return attachments.list(ticketId, auth);
    }

    @PostMapping(value = "/{ticketId}/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TicketAttachmentResponse> upload(@PathVariable Long ticketId,
                                                            @RequestPart("file") MultipartFile file,
                                                            @AuthenticationPrincipal AuthenticatedUser auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(attachments.upload(ticketId, file, auth));
    }

    @GetMapping("/attachments/{attachmentId}/content")
    public ResponseEntity<Resource> content(@PathVariable Long attachmentId,
                                             @AuthenticationPrincipal AuthenticatedUser auth) {
        var content = attachments.content(attachmentId, auth);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(content.contentType()));
        headers.setContentLength(content.sizeBytes());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.fileName(), StandardCharsets.UTF_8).build());
        headers.setCacheControl(CacheControl.noStore().cachePrivate());
        return ResponseEntity.ok().headers(headers).body(new FileSystemResource(content.path()));
    }
}
