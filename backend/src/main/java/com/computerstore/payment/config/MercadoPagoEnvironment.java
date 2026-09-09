package com.computerstore.payment.config;

public enum MercadoPagoEnvironment {
    SANDBOX,
    TEST_ACCOUNT,
    PRODUCTION;

    public boolean usesSandboxCheckout() {
        return this != PRODUCTION;
    }

    public boolean expectsLiveMode() {
        return this != SANDBOX;
    }
}
