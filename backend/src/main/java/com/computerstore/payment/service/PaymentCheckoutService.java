package com.computerstore.payment.service;

import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.exception.PaymentProviderException;
import com.computerstore.payment.gateway.MercadoPagoGateway;
import org.springframework.stereotype.Service;

@Service
public class PaymentCheckoutService {

    private final PaymentAttemptTransactionalService transactions;
    private final MercadoPagoGateway gateway;

    public PaymentCheckoutService(PaymentAttemptTransactionalService transactions, MercadoPagoGateway gateway) {
        this.transactions = transactions;
        this.gateway = gateway;
    }

    public CheckoutResult create(Long orderId, Long userId, String idempotencyKey) {
        PaymentPreparation preparation = transactions.prepare(orderId, userId, idempotencyKey);
        return create(preparation);
    }

    public CheckoutResult createGuest(Long orderId, String idempotencyKey) {
        return create(transactions.prepareGuest(orderId, idempotencyKey));
    }

    private CheckoutResult create(PaymentPreparation preparation) {
        if (preparation.response() != null) {
            return new CheckoutResult(false, preparation.response());
        }
        try {
            var preference = gateway.createPreference(preparation.preferenceRequest());
            PaymentCheckoutResponse response = transactions.completePreference(
                    preparation.preferenceRequest().attemptId(), preference);
            return new CheckoutResult(preparation.created(), response);
        } catch (PaymentProviderException exception) {
            transactions.recordPreferenceFailure(
                    preparation.preferenceRequest().attemptId(), exception.getMessage());
            throw exception;
        }
    }

    public record CheckoutResult(boolean created, PaymentCheckoutResponse response) {
    }
}
