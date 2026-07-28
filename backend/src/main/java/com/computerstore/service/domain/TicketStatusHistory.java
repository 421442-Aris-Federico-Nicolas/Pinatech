package com.computerstore.service.domain;

import java.time.Instant;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;

@Entity
@Table(name = "ticket_status_history")
public class TicketStatusHistory {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "ticket_id", nullable = false) private TechnicalServiceTicket ticket;
    @Enumerated(EnumType.STRING) @Column(name = "previous_status") private TicketStatus previousStatus;
    @Enumerated(EnumType.STRING) @Column(name = "new_status", nullable = false) private TicketStatus newStatus;
    @Column(length = 1000) private String comment;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "changed_by_user_id", nullable = false) private UserAccount changedBy;
    @Column(name = "changed_at", nullable = false, updatable = false) private Instant changedAt;

    protected TicketStatusHistory() {}
    public TicketStatusHistory(TechnicalServiceTicket ticket, TicketStatus previousStatus, TicketStatus newStatus, String comment, UserAccount changedBy) {
        this.ticket = ticket;
        this.previousStatus = previousStatus;
        this.newStatus = newStatus;
        this.comment = comment == null || comment.isBlank() ? null : comment.trim();
        this.changedBy = changedBy;
    }
    @PrePersist void created() { changedAt = Instant.now(); }
    public Long getId() { return id; } public TicketStatus getPreviousStatus() { return previousStatus; } public TicketStatus getNewStatus() { return newStatus; }
    public String getComment() { return comment; } public UserAccount getChangedBy() { return changedBy; } public Instant getChangedAt() { return changedAt; }
}
