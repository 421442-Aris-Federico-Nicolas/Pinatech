package com.computerstore.service.service;

import com.computerstore.common.exception.BusinessRuleException;
import com.computerstore.common.exception.ResourceNotFoundException;
import com.computerstore.common.exception.UnauthorizedResourceAccessException;
import com.computerstore.security.AuthenticatedUser;
import com.computerstore.service.domain.TechnicalServiceTicket;
import com.computerstore.service.domain.TicketAttachment;
import com.computerstore.service.domain.UploaderRole;
import com.computerstore.service.repository.TechnicalServiceTicketRepository;
import com.computerstore.service.repository.TicketAttachmentRepository;
import com.computerstore.storage.LocalImageStorage;
import com.computerstore.user.domain.UserAccount;
import com.computerstore.user.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TicketAttachmentServiceTest {
    @Mock TechnicalServiceTicketRepository tickets;
    @Mock TicketAttachmentRepository attachments;
    @Mock UserAccountRepository users;
    @Mock LocalImageStorage storage;

    private TicketAttachmentService service;
    private UserAccount owner;
    private TechnicalServiceTicket ticket;

    @BeforeEach
    void setUp() {
        service = new TicketAttachmentService(tickets, attachments, users, storage);
        owner = user(10L, "Owner", "Customer");
        ticket = new TechnicalServiceTicket(owner, "Notebook", "Pinatech", "Lab", "No enciende");
        ReflectionTestUtils.setField(ticket, "id", 1L);
    }

    @Test
    void customerCanReadOwnTicketButNotAnotherCustomersTicket() {
        when(tickets.findById(1L)).thenReturn(Optional.of(ticket));
        when(attachments.findByTicketIdOrderByCreatedAtAscIdAsc(1L)).thenReturn(List.of());

        assertEquals(List.of(), service.list(1L, auth(10L, "CUSTOMER")));
        assertThrows(ResourceNotFoundException.class,
                () -> service.list(1L, auth(11L, "CUSTOMER")));
    }

    @Test
    void assignedTechnicianCanUpload() {
        UserAccount technician = user(20L, "Tech", "One");
        ticket.assignTechnician(technician);
        MockMultipartFile file = new MockMultipartFile("file", "board.png", "image/png", new byte[]{1});
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(attachments.countByTicketId(1L)).thenReturn(0L);
        when(users.findById(20L)).thenReturn(Optional.of(technician));
        when(storage.store(file)).thenReturn(new LocalImageStorage.StoredImage(
                "3d45a4c2-a70c-4e87-99d3-bd26e2601e15", "board.png", "image/png", 100));
        when(attachments.saveAndFlush(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 5L);
            return saved;
        });

        var response = service.upload(1L, file, auth(20L, "TECHNICIAN"));

        assertEquals(5L, response.id());
        assertEquals(UploaderRole.TECHNICIAN, response.uploaderRole());
        verify(storage).store(file);
    }

    @Test
    void technicianCannotUploadToTicketAssignedToSomeoneElse() {
        ticket.assignTechnician(user(21L, "Other", "Tech"));
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));

        assertThrows(UnauthorizedResourceAccessException.class,
                () -> service.upload(1L, mock(MockMultipartFile.class), auth(20L, "TECHNICIAN")));
        verifyNoInteractions(storage);
    }

    @Test
    void customerQuotaUsesStoredSizeAndDeletesRejectedFile() {
        MockMultipartFile file = new MockMultipartFile("file", "board.png", "image/png", new byte[]{1});
        String storageKey = "3d45a4c2-a70c-4e87-99d3-bd26e2601e15";
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(attachments.countByTicketId(1L)).thenReturn(0L);
        when(storage.store(file)).thenReturn(new LocalImageStorage.StoredImage(
                storageKey, "board.png", "image/png", 2L * 1024 * 1024));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(attachments.sumCustomerSizeBytes(10L)).thenReturn(249L * 1024 * 1024);

        assertThrows(BusinessRuleException.class,
                () -> service.upload(1L, file, auth(10L, "CUSTOMER")));

        verify(users).findByIdForUpdate(10L);
        verify(storage).delete(storageKey);
        verify(attachments, never()).saveAndFlush(any());
    }

    @Test
    void customerCanReachQuotaExactly() {
        MockMultipartFile file = new MockMultipartFile("file", "board.png", "image/png", new byte[]{1});
        when(tickets.findByIdForUpdate(1L)).thenReturn(Optional.of(ticket));
        when(attachments.countByTicketId(1L)).thenReturn(0L);
        when(storage.store(file)).thenReturn(new LocalImageStorage.StoredImage(
                "3d45a4c2-a70c-4e87-99d3-bd26e2601e15", "board.png", "image/png", 5L * 1024 * 1024));
        when(users.findByIdForUpdate(10L)).thenReturn(Optional.of(owner));
        when(attachments.sumCustomerSizeBytes(10L)).thenReturn(245L * 1024 * 1024);
        when(attachments.saveAndFlush(any(TicketAttachment.class))).thenAnswer(invocation -> {
            TicketAttachment saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 6L);
            return saved;
        });

        var response = service.upload(1L, file, auth(10L, "CUSTOMER"));

        assertEquals(6L, response.id());
        assertEquals(UploaderRole.CUSTOMER, response.uploaderRole());
        verify(attachments).sumCustomerSizeBytes(10L);
    }

    @Test
    void customerCannotReadAnotherCustomersAttachment() {
        UserAccount otherCustomer = user(11L, "Other", "Customer");
        TechnicalServiceTicket otherTicket = new TechnicalServiceTicket(
                otherCustomer, "Notebook", "Pinatech", "Lab", "No enciende");
        TicketAttachment attachment = new TicketAttachment(otherTicket, otherCustomer, UploaderRole.CUSTOMER,
                "3d45a4c2-a70c-4e87-99d3-bd26e2601e15", "board.png", "image/png", 100);
        when(attachments.findById(5L)).thenReturn(Optional.of(attachment));

        assertThrows(ResourceNotFoundException.class,
                () -> service.content(5L, auth(10L, "CUSTOMER")));
        verifyNoInteractions(storage);
    }

    private UserAccount user(Long id, String firstName, String lastName) {
        UserAccount user = new UserAccount(firstName, lastName, firstName.toLowerCase() + "@example.com", "hash", null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private AuthenticatedUser auth(Long id, String role) {
        return new AuthenticatedUser(id, "user@example.com", List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
}
