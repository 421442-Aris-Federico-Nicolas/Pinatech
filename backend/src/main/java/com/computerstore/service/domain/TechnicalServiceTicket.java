package com.computerstore.service.domain;

import java.time.Instant;
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
    @Enumerated(EnumType.STRING) @Column(nullable = false) private TicketStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private TicketPriority priority;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected TechnicalServiceTicket() {}
    public TechnicalServiceTicket(UserAccount customer, String deviceType, String brand, String model, String serialNumber, String reportedProblem) { this.customer = customer; this.deviceType = deviceType; this.brand = brand; this.model = model; this.serialNumber = serialNumber; this.reportedProblem = reportedProblem; this.status = TicketStatus.RECEIVED; this.priority = TicketPriority.NORMAL; }
    public void updateStatus(TicketStatus status) { this.status = status; }
    public void assignTechnician(UserAccount technician) { this.technician = technician; }
    @PrePersist void created() { createdAt = Instant.now(); updatedAt = createdAt; }
    @PreUpdate void updated() { updatedAt = Instant.now(); }
    public Long getId() { return id; } public UserAccount getCustomer() { return customer; } public UserAccount getTechnician() { return technician; }
    public String getDeviceType() { return deviceType; } public String getBrand() { return brand; } public String getModel() { return model; } public String getSerialNumber() { return serialNumber; } public String getReportedProblem() { return reportedProblem; }
    public TicketStatus getStatus() { return status; } public TicketPriority getPriority() { return priority; } public Instant getCreatedAt() { return createdAt; }
}
