package com.computerstore.service.service;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.common.exception.UnauthorizedResourceAccessException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.domain.TicketAttachment;
import com.computerstore.service.domain.UploaderRole;
import com.computerstore.service.dto.TicketAttachmentResponse;
import com.computerstore.service.repository.TechnicalServiceTicketRepository;
import com.computerstore.service.repository.TicketAttachmentRepository;
import com.computerstore.storage.LocalImageStorage;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TicketAttachmentService {
    private static final int MAX_ATTACHMENTS = 10;
    private static final long CUSTOMER_STORAGE_QUOTA = 250L * 1024 * 1024;
    private final TechnicalServiceTicketRepository tickets;
    private final TicketAttachmentRepository attachments;
    private final UserAccountRepository users;
    private final LocalImageStorage storage;

    public TicketAttachmentService(TechnicalServiceTicketRepository tickets, TicketAttachmentRepository attachments,
                                   UserAccountRepository users, LocalImageStorage storage) {
        this.tickets = tickets;
        this.attachments = attachments;
        this.users = users;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> list(Long ticketId, AuthenticatedUser auth) {
        TechnicalServiceTicket ticket = ticket(ticketId);
        authorizeRead(ticket, auth);
        return attachments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream().map(this::response).toList();
    }

    @Transactional
    public TicketAttachmentResponse upload(Long ticketId, MultipartFile file, AuthenticatedUser auth) {
        TechnicalServiceTicket ticket = tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found."));
        UploaderRole role = authorizeUpload(ticket, auth);
        if (attachments.countByTicketId(ticketId) >= MAX_ATTACHMENTS) {
            throw new BusinessRuleException("A ticket cannot have more than 10 attachments.");
        }
        LocalImageStorage.StoredImage stored = storage.store(file);
        cleanupOnRollback(stored.storageKey());
        try {
            UserAccount uploader = role == UploaderRole.CUSTOMER
                    ? users.findByIdForUpdate(auth.id())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found."))
                    : users.findById(auth.id())
                            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            if (role == UploaderRole.CUSTOMER
                    && attachments.sumCustomerSizeBytes(auth.id()) > CUSTOMER_STORAGE_QUOTA - stored.sizeBytes()) {
                throw new BusinessRuleException("Customer ticket attachments cannot exceed 250 MiB in total.");
            }
            TicketAttachment attachment = attachments.saveAndFlush(new TicketAttachment(ticket, uploader, role,
                    stored.storageKey(), stored.originalFilename(), stored.contentType(), stored.sizeBytes()));
            return response(attachment);
        } catch (RuntimeException exception) {
            try {
                storage.delete(stored.storageKey());
            } catch (RuntimeException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AttachmentContent content(Long attachmentId, AuthenticatedUser auth) {
        TicketAttachment attachment = attachments.findById(attachmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket attachment not found."));
        authorizeRead(attachment.getTicket(), auth);
        return new AttachmentContent(storage.load(attachment.getStorageKey()), attachment.getContentType(),
                attachment.getOriginalFilename(), attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public List<TicketAttachmentResponse> responsesForTicket(Long ticketId) {
        return attachments.findByTicketIdOrderByCreatedAtAscIdAsc(ticketId).stream().map(this::response).toList();
    }

    @Transactional(readOnly = true)
    public Map<Long, List<TicketAttachmentResponse>> responsesByTicketIds(Collection<Long> ticketIds) {
        if (ticketIds.isEmpty()) {
            return Map.of();
        }
        return attachments.findByTicketIdInOrderByTicketIdAscCreatedAtAscIdAsc(ticketIds).stream()
                .collect(Collectors.groupingBy(item -> item.getTicket().getId(),
                        Collectors.mapping(this::response, Collectors.toList())));
    }

    private UploaderRole authorizeUpload(TechnicalServiceTicket ticket, AuthenticatedUser auth) {
        if (hasRole(auth, "ADMIN")) {
            return UploaderRole.ADMIN;
        }
        if (hasRole(auth, "TECHNICIAN")) {
            if (ticket.getTechnician() == null || ticket.getTechnician().getId().equals(auth.id())) {
                return UploaderRole.TECHNICIAN;
            }
            throw new UnauthorizedResourceAccessException("Ticket is assigned to another technician.");
        }
        if (hasRole(auth, "CUSTOMER") && ticket.getCustomer().getId().equals(auth.id())) {
            return UploaderRole.CUSTOMER;
        }
        throw new UnauthorizedResourceAccessException("You cannot access this ticket.");
    }

    private void authorizeRead(TechnicalServiceTicket ticket, AuthenticatedUser auth) {
        if (hasRole(auth, "ADMIN") || hasRole(auth, "TECHNICIAN")) {
            return;
        }
        if (!hasRole(auth, "CUSTOMER") || !ticket.getCustomer().getId().equals(auth.id())) {
            throw new ResourceNotFoundException("Ticket not found.");
        }
    }

    private boolean hasRole(AuthenticatedUser auth, String role) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_" + role));
    }

    private TechnicalServiceTicket ticket(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found."));
    }

    private TicketAttachmentResponse response(TicketAttachment attachment) {
        UserAccount uploader = attachment.getUploadedBy();
        return new TicketAttachmentResponse(attachment.getId(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getSizeBytes(),
                uploader.getFirstName() + " " + uploader.getLastName(), attachment.getUploaderRole(), attachment.getCreatedAt());
    }

    private void cleanupOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        storage.delete(storageKey);
                    } catch (RuntimeException ignored) {
                        // Database consistency takes priority; cleanup was best-effort.
                    }
                }
            }
        });
    }

    public record AttachmentContent(Path path, String contentType, String fileName, long sizeBytes) {}
}
