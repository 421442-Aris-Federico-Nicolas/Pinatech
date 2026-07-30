package com.computerstore.service.controller;

import java.util.List;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.domain.TicketStatusHistory;
import com.computerstore.service.dto.CreateTicketRequest;
import com.computerstore.service.dto.TicketResponse;
import com.computerstore.service.repository.TechnicalServiceTicketRepository;
import com.computerstore.service.repository.TicketStatusHistoryRepository;
import com.computerstore.service.service.TicketAttachmentService;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
public class TechnicalServiceTicketController {
    private final TechnicalServiceTicketRepository tickets;
    private final TicketStatusHistoryRepository history;
    private final UserAccountRepository users;
    private final TicketAttachmentService attachments;

    public TechnicalServiceTicketController(TechnicalServiceTicketRepository tickets, TicketStatusHistoryRepository history, UserAccountRepository users, TicketAttachmentService attachments) {
        this.tickets = tickets;
        this.history = history;
        this.users = users;
        this.attachments = attachments;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public ResponseEntity<TicketResponse> create(
            @Valid @RequestBody CreateTicketRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth
    ) {
        var user = users.findById(auth.id()).orElseThrow(() -> new ResourceNotFoundException("User not found."));
        var ticket = tickets.save(new TechnicalServiceTicket(
                user,
                request.deviceType().trim(),
                request.brand() == null ? "" : request.brand().trim(),
                request.model() == null ? "" : request.model().trim(),
                request.reportedProblem().trim()));
        history.save(new TicketStatusHistory(ticket, null, ticket.getStatus(), "Solicitud creada por el cliente.", user));
        return ResponseEntity.status(HttpStatus.CREATED).body(response(ticket));
    }

    @GetMapping("/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<TicketResponse> mine(@org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth) {
        List<TechnicalServiceTicket> customerTickets = tickets.findByCustomerIdOrderByCreatedAtDesc(auth.id());
        var attachmentMap = attachments.responsesByTicketIds(customerTickets.stream().map(TechnicalServiceTicket::getId).toList());
        return customerTickets.stream()
                .map(ticket -> response(ticket, attachmentMap.getOrDefault(ticket.getId(), List.of())))
                .toList();
    }

    private TicketResponse response(TechnicalServiceTicket ticket) {
        return response(ticket, attachments.responsesForTicket(ticket.getId()));
    }

    private TicketResponse response(TechnicalServiceTicket ticket, List<com.computerstore.service.dto.TicketAttachmentResponse> ticketAttachments) {
        return new TicketResponse(ticket.getId(), ticket.getDeviceType(), ticket.getBrand(), ticket.getModel(),
                ticket.getReportedProblem(), ticket.getStatus().name(), ticket.getCreatedAt(), ticketAttachments);
    }
}
