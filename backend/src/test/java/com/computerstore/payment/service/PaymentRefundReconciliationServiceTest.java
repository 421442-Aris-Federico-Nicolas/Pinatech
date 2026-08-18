package com.computerstore.payment.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentRefundReconciliationServiceTest {

    @Test
    void retriesPendingRefundWithItsPersistedIdempotencyKey() {
        MercadoPagoGateway gateway = Mockito.mock(MercadoPagoGateway.class);
        PaymentAttemptTransactionalService transactions = Mockito.mock(PaymentAttemptTransactionalService.class);
        RefundInstruction instruction = new RefundInstruction(
                UUID.randomUUID(), "123", UUID.randomUUID(), null);
        when(transactions.pendingRefunds()).thenReturn(List.of(instruction));
        when(gateway.refund("123", instruction.idempotencyKey())).thenReturn("refund-1");
        PaymentRefundReconciliationService reconciliation = new PaymentRefundReconciliationService(
                properties(), gateway, transactions);

        reconciliation.reconcile();

        verify(transactions).refundCompleted(instruction, "refund-1");
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true,
                MercadoPagoEnvironment.SANDBOX,
                "access-token",
                "webhook-secret",
                "99",
                URI.create("https://store.example"),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2));
    }
}
