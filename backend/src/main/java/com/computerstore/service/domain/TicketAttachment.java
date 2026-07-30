package com.computerstore.service.domain;

import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "ticket_attachments")
public class TicketAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ticket_id", nullable = false)
    private TechnicalServiceTicket ticket;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by_user_id", nullable = false)
    private UserAccount uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploader_role", nullable = false, length = 20)
    private UploaderRole uploaderRole;

    @Column(name = "storage_key", nullable = false, unique = true, length = 36)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TicketAttachment() {}

    public TicketAttachment(TechnicalServiceTicket ticket, UserAccount uploadedBy, UploaderRole uploaderRole,
                            String storageKey, String originalFilename, String contentType, long sizeBytes) {
        this.ticket = ticket;
        this.uploadedBy = uploadedBy;
        this.uploaderRole = uploaderRole;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
    }

    @PrePersist
    void created() { createdAt = Instant.now(); }

    public Long getId() { return id; }
    public TechnicalServiceTicket getTicket() { return ticket; }
    public UserAccount getUploadedBy() { return uploadedBy; }
    public UploaderRole getUploaderRole() { return uploaderRole; }
    public String getStorageKey() { return storageKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public Instant getCreatedAt() { return createdAt; }
}
