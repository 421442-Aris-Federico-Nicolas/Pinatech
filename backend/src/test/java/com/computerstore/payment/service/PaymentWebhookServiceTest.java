package com.computerstore.payment.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import com.computerstore.payment.gateway.ProviderPayment;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class PaymentWebhookServiceTest {

    @Test
    void failedRefundIsPersistedForAnIdempotentRetry() {
        MercadoPagoGateway gateway = Mockito.mock(MercadoPagoGateway.class);
        PaymentAttemptTransactionalService transactions = Mockito.mock(PaymentAttemptTransactionalService.class);
        PaymentWebhookService service = new PaymentWebhookService(gateway, transactions);
        ProviderPayment payment = new ProviderPayment(
                "123", UUID.randomUUID().toString(), "pref-1", "99", BigDecimal.TEN, "ARS",
                "approved", "accredited", Instant.now(), Instant.now(), "{}");
        RefundInstruction refund = new RefundInstruction(
                UUID.randomUUID(), "123", UUID.randomUUID(), 7L);
        when(gateway.getPayment("123")).thenReturn(payment);
        when(transactions.processWebhook(payment, "123", "request-1", "{}"))
                .thenReturn(Optional.of(refund));
        when(gateway.refund("123", refund.idempotencyKey()))
                .thenThrow(new PaymentProviderException("refund unavailable"));

        assertThrows(PaymentProviderException.class,
                () -> service.process("123", "request-1", "{}"));

        verify(transactions).refundFailed(refund, "refund unavailable");
    }
}
