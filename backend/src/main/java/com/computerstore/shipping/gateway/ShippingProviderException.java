package com.computerstore.shipping.gateway;

import java.time.Duration;

public class ShippingProviderException extends RuntimeException {
    private final Duration retryAfter;
    private final boolean ambiguous;
    private final boolean retryable;

    public ShippingProviderException(String message, Duration retryAfter, boolean ambiguous, boolean retryable,
                                     Throwable cause) {
        super(message, cause);
        this.retryAfter = retryAfter;
        this.ambiguous = ambiguous;
        this.retryable = retryable;
    }

    public Duration retryAfter() { return retryAfter; }
    public boolean ambiguous() { return ambiguous; }
    public boolean retryable() { return retryable; }
}
