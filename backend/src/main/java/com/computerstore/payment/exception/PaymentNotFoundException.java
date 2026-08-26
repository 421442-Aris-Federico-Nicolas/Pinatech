package com.computerstore.payment.exception;

public class PaymentNotFoundException extends PaymentProviderException {
    public PaymentNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
