package com.computerstore.service.domain;

import java.math.BigDecimal;
import java.time.Instant;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.user.domain.UserAccount;
import jakarta.persistence.*;

@Entity
@Table(name = "technical_service_tickets")
public class TechnicalServiceTicket {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "customer_id", nullable = false) private UserAccount customer;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "technician_id") private UserAccount technician;
    @Column(name = "device_type", nullable = false) private String deviceType;
    @Column(nullable = false) private String brand;
    @Column(nullable = false) private String model;
    @Column(name = "serial_number") private String serialNumber;
    @Column(name = "reported_problem", nullable = false) private String reportedProblem;
    @Column(length = 3000) private String diagnosis;
    @Column(name = "estimated_price", precision = 19, scale = 2) private BigDecimal estimatedPrice;
    @Column(name = "final_price", precision = 19, scale = 2) private BigDecimal finalPrice;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TicketStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TicketPriority priority;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected TechnicalServiceTicket() {}
    public TechnicalServiceTicket(UserAccount customer, String deviceType, String brand, String model, String serialNumber, String reportedProblem) { this.customer = customer; this.deviceType = deviceType; this.brand = brand; this.model = model; this.serialNumber = serialNumber; this.reportedProblem = reportedProblem; this.status = TicketStatus.RECEIVED; this.priority = TicketPriority.NORMAL; }
    public void updateStatus(TicketStatus target) {
        if (target == status) return;
        boolean valid = switch (status) {
            case RECEIVED -> target == TicketStatus.UNDER_DIAGNOSIS || target == TicketStatus.CANCELLED;
            case UNDER_DIAGNOSIS -> target == TicketStatus.WAITING_FOR_APPROVAL || target == TicketStatus.IN_REPAIR || target == TicketStatus.CANCELLED;
            case WAITING_FOR_APPROVAL -> target == TicketStatus.APPROVED || target == TicketStatus.CANCELLED;
            case APPROVED -> target == TicketStatus.IN_REPAIR || target == TicketStatus.CANCELLED;
            case IN_REPAIR -> target == TicketStatus.WAITING_FOR_PARTS || target == TicketStatus.READY_FOR_PICKUP || target == TicketStatus.CANCELLED;
            case WAITING_FOR_PARTS -> target == TicketStatus.IN_REPAIR || target == TicketStatus.CANCELLED;
            case READY_FOR_PICKUP -> target == TicketStatus.DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
        if (!valid) throw new InvalidStateTransitionException("The requested ticket status transition is not allowed.");
        status = target;
    }
    public void updateDetails(TicketPriority priority, String diagnosis, BigDecimal estimatedPrice, BigDecimal finalPrice) {
        this.priority = priority;
        this.diagnosis = normalize(diagnosis);
        this.estimatedPrice = estimatedPrice;
        this.finalPrice = finalPrice;
    }
    public void assignTechnician(UserAccount technician) { this.technician = technician; }
    private String normalize(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public UserAccount getCustomer() { return customer; } public UserAccount getTechnician() { return technician; }
    public String getDeviceType() { return deviceType; } public String getBrand() { return brand; } public String getModel() { return model; } public String getSerialNumber() { return serialNumber; } public String getReportedProblem() { return reportedProblem; }
    public String getDiagnosis() { return diagnosis; } public BigDecimal getEstimatedPrice() { return estimatedPrice; } public BigDecimal getFinalPrice() { return finalPrice; }
    public TicketStatus getStatus() { return status; } public TicketPriority getPriority() { return priority; } public Instant getCreatedAt() { return createdAt; } public Instant getUpdatedAt() { return updatedAt; }
}
