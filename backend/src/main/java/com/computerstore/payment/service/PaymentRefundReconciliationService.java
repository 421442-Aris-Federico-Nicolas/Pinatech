package com.computerstore.payment.service;

import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class PaymentRefundReconciliationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentRefundReconciliationService.class);

    private final MercadoPagoProperties properties;
    private final MercadoPagoGateway gateway;
    private final PaymentAttemptTransactionalService transactions;

    public PaymentRefundReconciliationService(
            MercadoPagoProperties properties,
            MercadoPagoGateway gateway,
            PaymentAttemptTransactionalService transactions
    ) {
        this.properties = properties;
        this.gateway = gateway;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${app.payments.mercado-pago.refund-reconciliation-interval-ms:60000}")
    public void reconcile() {
        if (!properties.enabled()) {
            return;
        }
        for (RefundInstruction instruction : transactions.pendingRefunds()) {
            try {
                String refundId = gateway.refund(instruction.paymentId(), instruction.idempotencyKey());
                transactions.refundCompleted(instruction, refundId);
            } catch (PaymentProviderException exception) {
                transactions.refundFailed(instruction, exception.getMessage());
                LOGGER.warn("Mercado Pago refund reconciliation failed for attempt {}", instruction.attemptId());
            }
        }
    }
}
