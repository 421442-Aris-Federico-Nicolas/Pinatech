package com.computerstore.payment.service;

import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import com.computerstore.payment.gateway.RefundResult;
import org.springframework.stereotype.Service;

@Service
public class PaymentWebhookService {

    private final MercadoPagoGateway gateway;
    private final PaymentAttemptTransactionalService transactions;

    public PaymentWebhookService(MercadoPagoGateway gateway, PaymentAttemptTransactionalService transactions) {
        this.gateway = gateway;
        this.transactions = transactions;
    }

    public void process(String paymentId, String requestId, String notificationPayload) {
        var payment = gateway.getPayment(paymentId);
        var refund = transactions.processWebhook(payment, paymentId, requestId, notificationPayload);
        if (refund.isEmpty()) {
            return;
        }
        RefundInstruction instruction = refund.get();
        try {
            RefundResult result = instruction.refundId() == null
                    ? gateway.refund(instruction.paymentId(), instruction.idempotencyKey())
                    : gateway.getRefund(instruction.paymentId(), instruction.refundId());
            transactions.applyRefundResult(instruction, result);
        } catch (PaymentProviderException exception) {
            transactions.refundFailed(instruction, exception.getMessage());
            throw exception;
        }
    }
}
