package com.computerstore.payment.gateway;

import java.util.List;
import java.util.UUID;

public interface MercadoPagoGateway {
    PaymentPreference createPreference(PaymentPreferenceRequest request);
    ProviderPayment getPayment(String paymentId);
    List<String> findPaymentIdsByPreference(String preferenceId);
    RefundResult refund(String paymentId, UUID idempotencyKey);
    RefundResult getRefund(String paymentId, String refundId);
}
