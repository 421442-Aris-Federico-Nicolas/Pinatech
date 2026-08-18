package com.computerstore.payment.service;

import com.computerstore.payment.dto.PaymentCheckoutResponse;
import com.computerstore.payment.gateway.PaymentPreferenceRequest;

record PaymentPreparation(
        boolean created,
        PaymentCheckoutResponse response,
        PaymentPreferenceRequest preferenceRequest
) {
}
