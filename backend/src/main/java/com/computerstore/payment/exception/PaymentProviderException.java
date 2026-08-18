package com.computerstore.payment.exception;

public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }

    public PaymentProviderException(String message) {
        super(message);
    }
}
