package com.computerstore.payment.service;

import com.computerstore.payment.config.MercadoPagoProperties;
import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import com.computerstore.payment.gateway.RefundResult;
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
        if (!properties.enabled()) return;

        for (ReconciliationInstruction instruction : transactions.claimReconciliations()) {
            try {
                for (String paymentId : gateway.findPaymentIdsByPreference(instruction.preferenceId())) {
                    var refund = transactions.processReconciledPayment(gateway.getPayment(paymentId));
                    refund.ifPresent(this::reconcileRefund);
                }
                transactions.reconciliationSucceeded(instruction.attemptId());
            } catch (RuntimeException exception) {
                transactions.reconciliationFailed(instruction.attemptId(), exception.getMessage());
                LOGGER.warn("Mercado Pago payment reconciliation failed for attempt {}", instruction.attemptId());
            }
        }

        for (RefundInstruction instruction : transactions.claimPendingRefunds()) {
            reconcileRefund(instruction);
        }
    }

    private void reconcileRefund(RefundInstruction instruction) {
        try {
            RefundResult result = instruction.refundId() == null
                    ? gateway.refund(instruction.paymentId(), instruction.idempotencyKey())
                    : gateway.getRefund(instruction.paymentId(), instruction.refundId());
            transactions.applyRefundResult(instruction, result);
        } catch (PaymentProviderException exception) {
            transactions.refundFailed(instruction, exception.getMessage());
            LOGGER.warn("Mercado Pago refund reconciliation failed for payment {}", instruction.paymentId());
        }
    }
}
