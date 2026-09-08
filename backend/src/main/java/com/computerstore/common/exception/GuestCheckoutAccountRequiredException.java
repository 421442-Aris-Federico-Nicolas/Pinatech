package com.computerstore.common.exception;

public class GuestCheckoutAccountRequiredException extends RuntimeException {

    public GuestCheckoutAccountRequiredException() {
        super("An account already exists for this email. Sign in to continue.");
    }
}
