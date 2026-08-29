package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.common.exception.InvalidRequestException;
import com.computerstore.email.OrderEmailEventType;
import com.computerstore.email.OrderEmailOutboxService;
import com.computerstore.order.domain.CustomerOrder;
import com.computerstore.order.domain.OrderStatus;
import com.computerstore.order.repository.CustomerOrderRepository;
import com.computerstore.order.service.OrderStockService;
import com.computerstore.payment.domain.BankTransferProof;
import com.computerstore.payment.domain.BankTransferProofStatus;
import com.computerstore.payment.repository.BankTransferProofRepository;
import com.computerstore.storage.PrivateDocumentStorage;
import com.computerstore.security.AuthenticatedUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BankTransferServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-29T10:00:00Z");

    private final CustomerOrderRepository orders = mock(CustomerOrderRepository.class);
    private final BankTransferProofRepository proofs = mock(BankTransferProofRepository.class);
    private final OrderStockService stock = mock(OrderStockService.class);
    private final PrivateDocumentStorage storage = mock(PrivateDocumentStorage.class);
    private final OrderEmailOutboxService outbox = mock(OrderEmailOutboxService.class);
    private final BankTransferService service = new BankTransferService(
            orders, proofs, stock, storage, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
    private final UUID proofId = UUID.fromString("4d2e8ab1-46d7-4fd1-a711-7a4d60197be1");
    private final BankTransferProof proof = mock(BankTransferProof.class);
    private final CustomerOrder order = mock(CustomerOrder.class);
    private final AuthenticatedUser admin = new AuthenticatedUser(
            7L, "admin@example.com", java.util.List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

    @BeforeEach
    void setUpPendingProof() {
        when(proofs.findByIdForUpdate(proofId)).thenReturn(Optional.of(proof));
        when(proof.getStatus()).thenReturn(BankTransferProofStatus.PENDING_REVIEW);
        when(proof.getOrder()).thenReturn(order);
        when(order.getId()).thenReturn(41L);
        when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
        when(order.getTotal()).thenReturn(new BigDecimal("264.00"));
        when(orders.findByIdForUpdate(41L)).thenReturn(Optional.of(order));
    }

    @Test
    void approvesOnlyAnExactAmountAndPersistsTheNormalizedReference() {
        service.approve(proofId, new BigDecimal("264.00"), " ref-123 ", admin);

        verify(order).approveBankTransfer();
        verify(proof).approve(NOW, "REF123", new BigDecimal("264.00"), 7L);
        verify(outbox).enqueue(order, OrderEmailEventType.PAYMENT_APPROVED);
    }

    @Test
    void rejectsAnIncorrectAmountWithoutChangingTheOrder() {
        assertThrows(InvalidRequestException.class,
                () -> service.approve(proofId, new BigDecimal("263.99"), "REF123", admin));

        verify(order, never()).approveBankTransfer();
        verify(outbox, never()).enqueue(any(), eq(OrderEmailEventType.PAYMENT_APPROVED));
    }

    @Test
    void rejectionReleasesStockAndEnqueuesTheCustomerReason() {
        service.reject(proofId, " No se acreditó el importe ", admin);

        verify(stock).release(order);
        verify(order).rejectBankTransfer();
        verify(proof).reject(NOW, "No se acreditó el importe", 7L);
        verify(outbox).enqueue(order, OrderEmailEventType.BANK_TRANSFER_REJECTED,
                "No se acreditó el importe");
    }
}
