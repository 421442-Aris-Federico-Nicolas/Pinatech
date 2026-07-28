package com.computerstore.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import com.computerstore.common.exception.InvalidStateTransitionException;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.domain.TicketPriority;
import com.computerstore.service.domain.TicketStatus;
import com.computerstore.user.domain.UserAccount;
import org.junit.jupiter.api.Test;

class TechnicalServiceTicketTest {
    @Test
    void followsTheTechnicalWorkflowAndRejectsInvalidJumps() {
        var ticket = ticket();

        assertThrows(InvalidStateTransitionException.class, () -> ticket.updateStatus(TicketStatus.DELIVERED));
        ticket.updateStatus(TicketStatus.UNDER_DIAGNOSIS);
        ticket.updateStatus(TicketStatus.WAITING_FOR_APPROVAL);
        ticket.updateStatus(TicketStatus.APPROVED);
        ticket.updateStatus(TicketStatus.IN_REPAIR);
        ticket.updateStatus(TicketStatus.READY_FOR_PICKUP);
        ticket.updateStatus(TicketStatus.DELIVERED);

        assertEquals(TicketStatus.DELIVERED, ticket.getStatus());
        assertThrows(InvalidStateTransitionException.class, () -> ticket.updateStatus(TicketStatus.IN_REPAIR));
    }

    @Test
    void storesNormalizedTechnicalDetails() {
        var ticket = ticket();

        ticket.updateDetails(TicketPriority.HIGH, "  Fuente dañada  ", new BigDecimal("25000.00"), new BigDecimal("24000.00"));

        assertEquals(TicketPriority.HIGH, ticket.getPriority());
        assertEquals("Fuente dañada", ticket.getDiagnosis());
        assertEquals(new BigDecimal("25000.00"), ticket.getEstimatedPrice());
        assertEquals(new BigDecimal("24000.00"), ticket.getFinalPrice());
    }

    private TechnicalServiceTicket ticket() {
        return new TechnicalServiceTicket(
                new UserAccount("Ada", "Lovelace", "ada@example.com", "hash", null),
                "Notebook", "Pinatech", "Lab", null, "No enciende");
    }
}
