package com.computerstore.service.controller;

import java.util.List;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.dto.*;
import com.computerstore.service.repository.TechnicalServiceTicketRepository;
import com.computerstore.user.domain.RoleName;
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
    private final UserAccountRepository users;
    public TechnicalDashboardController(TechnicalServiceTicketRepository tickets, UserAccountRepository users) { this.tickets = tickets; this.users = users; }
    @GetMapping @Transactional(readOnly = true) public List<TechnicalTicketResponse> list() { return tickets.findAllByOrderByCreatedAtDesc().stream().map(this::response).toList(); }
    @GetMapping("/technicians") @PreAuthorize("hasRole('ADMIN')") public List<TechnicianResponse> technicians() { return users.findAll().stream().filter(user -> user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.TECHNICIAN)).map(user -> new TechnicianResponse(user.getId(), user.getFirstName() + " " + user.getLastName())).toList(); }
    @PatchMapping("/{id}/status") @Transactional public TechnicalTicketResponse status(@PathVariable Long id, @Valid @RequestBody TicketStatusRequest request, @org.springframework.security.core.annotation.AuthenticationPrincipal AuthenticatedUser auth) { var ticket = ticket(id); if (auth.getAuthorities().stream().anyMatch(role -> role.getAuthority().equals("ROLE_TECHNICIAN")) && ticket.getTechnician() == null) ticket.assignTechnician(user(auth.id())); ticket.updateStatus(request.status()); return response(ticket); }
    @PatchMapping("/{id}/technician") @PreAuthorize("hasRole('ADMIN')") @Transactional public TechnicalTicketResponse assign(@PathVariable Long id, @Valid @RequestBody AssignTechnicianRequest request) { var technician = user(request.technicianId()); if (!technician.getRoles().stream().anyMatch(role -> role.getName() == RoleName.TECHNICIAN)) throw new ResourceNotFoundException("Technician not found."); var ticket = ticket(id); ticket.assignTechnician(technician); return response(ticket); }
    private TechnicalServiceTicket ticket(Long id) { return tickets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Ticket not found.")); }
    private com.computerstore.user.domain.UserAccount user(Long id) { return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found.")); }
    private TechnicalTicketResponse response(TechnicalServiceTicket ticket) { var tech = ticket.getTechnician(); return new TechnicalTicketResponse(ticket.getId(), ticket.getCustomer().getFirstName() + " " + ticket.getCustomer().getLastName(), tech == null ? "Sin asignar" : tech.getFirstName() + " " + tech.getLastName(), ticket.getDeviceType(), ticket.getBrand(), ticket.getModel(), ticket.getReportedProblem(), ticket.getStatus().name(), ticket.getPriority().name(), ticket.getCreatedAt()); }
}
