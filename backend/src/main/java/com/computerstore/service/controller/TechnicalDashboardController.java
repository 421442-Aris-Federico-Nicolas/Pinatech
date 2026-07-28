package com.computerstore.service.controller;

import java.util.List;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.common.exception.UnauthorizedResourceAccessException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.domain.TicketStatusHistory;
import com.computerstore.service.dto.*;
import com.computerstore.service.repository.TechnicalServiceTicketRepository;
import com.computerstore.service.repository.TicketStatusHistoryRepository;
import com.computerstore.user.domain.RoleName;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/technical/tickets")
@PreAuthorize("hasAnyRole('TECHNICIAN','ADMIN')")
public class TechnicalDashboardController {
    private final TechnicalServiceTicketRepository tickets;
    private final TicketStatusHistoryRepository history;
    private final UserAccountRepository users;

    public TechnicalDashboardController(TechnicalServiceTicketRepository tickets, TicketStatusHistoryRepository history, UserAccountRepository users) {
        this.tickets = tickets;
        this.history = history;
        this.users = users;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<TechnicalTicketResponse> list() {
        return tickets.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList();
    }

    @GetMapping("/{id}/history")
    @Transactional(readOnly = true)
    public List<TicketHistoryResponse> history(@PathVariable Long id) {
        ticket(id);
        return history.findByTicketIdOrderByChangedAtAsc(id).stream()
                .map(item -> new TicketHistoryResponse(
                        item.getId(),
                        item.getPreviousStatus() == null ? null : item.getPreviousStatus().name(),
                        item.getNewStatus().name(),
                        item.getComment(),
                        item.getChangedBy().getFirstName() + " " + item.getChangedBy().getLastName(),
                        item.getChangedAt()))
                .toList();
    }

    @GetMapping("/technicians")
    @PreAuthorize("hasRole('ADMIN')")
    public List<TechnicianResponse> technicians() {
        return users.findAll().stream()
                .filter(UserAccount::isActive)
                .filter(user -> user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.TECHNICIAN))
                .map(user -> new TechnicianResponse(user.getId(), user.getFirstName() + " " + user.getLastName()))
                .toList();
    }

    @PatchMapping("/{id}/status")
    @Transactional
    public TechnicalTicketResponse status(
            @PathVariable Long id,
            @Valid @RequestBody TicketStatusRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth
    ) {
        var ticket = ticket(id);
        var actor = user(auth.id());
        claimOrAuthorize(ticket, actor, auth);
        var previous = ticket.getStatus();
        ticket.updateStatus(request.status());
        if (previous != ticket.getStatus()) {
            history.save(new TicketStatusHistory(ticket, previous, ticket.getStatus(), request.comment(), actor));
        }
        return response(ticket);
    }

    @PatchMapping("/{id}/details")
    @Transactional
    public TechnicalTicketResponse details(
            @PathVariable Long id,
            @Valid @RequestBody TechnicalTicketUpdateRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth
    ) {
        var ticket = ticket(id);
        claimOrAuthorize(ticket, user(auth.id()), auth);
        ticket.updateDetails(request.priority(), request.diagnosis(), request.estimatedPrice(), request.finalPrice());
        return response(ticket);
    }

    @PatchMapping("/{id}/technician")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public TechnicalTicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignTechnicianRequest request) {
        var technician = user(request.technicianId());
        if (!technician.isActive() || technician.getRoles().stream().noneMatch(role -> role.getName() == RoleName.TECHNICIAN)) {
            throw new ResourceNotFoundException("Technician not found.");
        }
        var ticket = ticket(id);
        ticket.assignTechnician(technician);
        return response(ticket);
    }

    @PatchMapping("/{id}/claim")
    @PreAuthorize("hasRole('TECHNICIAN')")
    @Transactional
    public TechnicalTicketResponse claim(
            @PathVariable Long id,
            @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth
    ) {
        var ticket = ticket(id);
        var technician = user(auth.id());
        if (ticket.getTechnician() != null && !ticket.getTechnician().getId().equals(technician.getId())) {
            throw new UnauthorizedResourceAccessException("Ticket is assigned to another technician.");
        }
        ticket.assignTechnician(technician);
        return response(ticket);
    }

    private void claimOrAuthorize(TechnicalServiceTicket ticket, UserAccount actor, AuthenticatedUser auth) {
        boolean technician = auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_TECHNICIAN"));
        if (!technician) return;
        if (ticket.getTechnician() == null) {
            ticket.assignTechnician(actor);
        } else if (!ticket.getTechnician().getId().equals(actor.getId())) {
            throw new UnauthorizedResourceAccessException("Ticket is assigned to another technician.");
        }
    }

    private TechnicalServiceTicket ticket(Long id) {
        return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found."));
    }

    private UserAccount user(Long id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private TechnicalTicketResponse response(TechnicalServiceTicket ticket) {
        var customer = ticket.getCustomer();
        var technician = ticket.getTechnician();
        return new TechnicalTicketResponse(
                ticket.getId(),
                customer.getFirstName() + " " + customer.getLastName(),
                customer.getEmail(),
                technician == null ? null : technician.getId(),
                technician == null ? "Sin asignar" : technician.getFirstName() + " " + technician.getLastName(),
                ticket.getDeviceType(),
                ticket.getBrand(),
                ticket.getModel(),
                ticket.getSerialNumber(),
                ticket.getReportedProblem(),
                ticket.getDiagnosis(),
                ticket.getEstimatedPrice(),
                ticket.getFinalPrice(),
                ticket.getStatus().name(),
                ticket.getPriority().name(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt());
    }
}
