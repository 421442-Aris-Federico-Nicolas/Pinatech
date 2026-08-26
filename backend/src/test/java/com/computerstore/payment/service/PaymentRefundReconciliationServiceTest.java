package com.computerstore.payment.service;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.payment.config.MercadoPagoEnvironment;
import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import com.computerstore.payment.gateway.ProviderPayment;
import com.computerstore.payment.gateway.RefundResult;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentRefundReconciliationServiceTest {

    @Test
    void reconcilesLostWebhookBySearchingThePreferenceAndReadingEveryPayment() {
        MercadoPagoGateway gateway = Mockito.mock(MercadoPagoGateway.class);
        PaymentAttemptTransactionalService transactions = Mockito.mock(PaymentAttemptTransactionalService.class);
        ReconciliationInstruction instruction = new ReconciliationInstruction(UUID.randomUUID(), "pref-1");
        ProviderPayment first = Mockito.mock(ProviderPayment.class);
        ProviderPayment second = Mockito.mock(ProviderPayment.class);
        when(transactions.claimReconciliations()).thenReturn(List.of(instruction));
        when(transactions.claimPendingRefunds()).thenReturn(List.of());
        when(gateway.findPaymentIdsByPreference("pref-1")).thenReturn(List.of("123", "124"));
        when(gateway.getPayment("123")).thenReturn(first);
        when(gateway.getPayment("124")).thenReturn(second);
        when(transactions.processReconciledPayment(first)).thenReturn(Optional.empty());
        when(transactions.processReconciledPayment(second)).thenReturn(Optional.empty());

        new PaymentRefundReconciliationService(properties(), gateway, transactions).reconcile();

        verify(transactions).processReconciledPayment(first);
        verify(transactions).processReconciledPayment(second);
        verify(transactions).reconciliationSucceeded(instruction.attemptId());
    }

    @Test
    void pendingRefundWithProviderIdUsesAuthoritativeGet() {
        MercadoPagoGateway gateway = Mockito.mock(MercadoPagoGateway.class);
        PaymentAttemptTransactionalService transactions = Mockito.mock(PaymentAttemptTransactionalService.class);
        RefundInstruction instruction = new RefundInstruction(
                UUID.randomUUID(), "123", UUID.randomUUID(), "refund-1", null);
        RefundResult result = new RefundResult("refund-1", "approved", BigDecimal.TEN);
        when(transactions.claimReconciliations()).thenReturn(List.of());
        when(transactions.claimPendingRefunds()).thenReturn(List.of(instruction));
        when(gateway.getRefund("123", "refund-1")).thenReturn(result);

        new PaymentRefundReconciliationService(properties(), gateway, transactions).reconcile();

        verify(transactions).applyRefundResult(instruction, result);
    }

    private MercadoPagoProperties properties() {
        return new MercadoPagoProperties(
                true, MercadoPagoEnvironment.SANDBOX, "TEST-access-token", "webhook-secret", "99",
                URI.create("https://store.example"), URI.create("https://api.example"),
                Duration.ofSeconds(1), Duration.ofSeconds(2),
                false, Duration.ofMinutes(5), Duration.ofDays(30));
    }
}
