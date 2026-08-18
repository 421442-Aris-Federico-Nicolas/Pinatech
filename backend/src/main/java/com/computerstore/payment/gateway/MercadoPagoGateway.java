package com.computerstore.payment.gateway;

import java.util.UUID;

public interface MercadoPagoGateway {
    PaymentPreference createPreference(PaymentPreferenceRequest request);
    ProviderPayment getPayment(String paymentId);
    String refund(String paymentId, UUID idempotencyKey);
}
